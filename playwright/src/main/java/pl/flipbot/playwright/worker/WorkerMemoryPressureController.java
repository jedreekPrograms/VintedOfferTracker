package pl.flipbot.playwright.worker;

import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * Protects the host from Chromium/page-file thrashing without permanently
 * sacrificing configured worker capacity.
 *
 * <p>Downscaling is immediate when physical memory pressure is high. Scaling
 * back up is deliberately slow and one slot at a time, so a machine that just
 * recovered from pressure cannot instantly relaunch several Chromium runtimes
 * and oscillate back to 99% RAM.</p>
 */
@Slf4j
final class WorkerMemoryPressureController {

    static final String ENABLED_ENV =
            "FLIPBOT_ADAPTIVE_MEMORY_SLOTS";

    static final double ELEVATED_USED_RATIO = 0.80d;
    static final double HIGH_USED_RATIO = 0.86d;
    static final double EMERGENCY_USED_RATIO = 0.92d;
    static final double RECOVERY_USED_RATIO = 0.72d;

    static final int ELEVATED_CAP = 8;
    static final int HIGH_CAP = 6;
    static final int EMERGENCY_CAP = 4;

    static final long RECOVERY_HOLD_NANOS =
            45_000_000_000L;

    private final int configuredMaxSlots;
    private final boolean enabled;
    private final MemoryProbe memoryProbe;
    private final LongSupplier nanoTime;

    private int adaptiveCap;
    private long healthySinceNanos = -1L;
    private String lastSummary = "unmeasured";

    private WorkerMemoryPressureController(
            int configuredMaxSlots,
            boolean enabled,
            MemoryProbe memoryProbe,
            LongSupplier nanoTime
    ) {
        if (configuredMaxSlots < 1) {
            throw new IllegalArgumentException(
                    "configuredMaxSlots must be at least 1"
            );
        }

        this.configuredMaxSlots = configuredMaxSlots;
        this.enabled = enabled;
        this.memoryProbe = memoryProbe;
        this.nanoTime = nanoTime;
        this.adaptiveCap = configuredMaxSlots;
    }

    static WorkerMemoryPressureController fromEnvironment(
            int configuredMaxSlots
    ) {
        return new WorkerMemoryPressureController(
                configuredMaxSlots,
                readEnabled(System.getenv(ENABLED_ENV)),
                WorkerMemoryPressureController::readSystemMemory,
                System::nanoTime
        );
    }

    static WorkerMemoryPressureController forTest(
            int configuredMaxSlots,
            boolean enabled,
            MemoryProbe memoryProbe,
            LongSupplier nanoTime
    ) {
        return new WorkerMemoryPressureController(
                configuredMaxSlots,
                enabled,
                memoryProbe,
                nanoTime
        );
    }

    synchronized int targetSlots(int requestedSlots) {
        int normalizedRequested = Math.max(
                0,
                Math.min(requestedSlots, configuredMaxSlots)
        );

        if (normalizedRequested == 0) {
            lastSummary = "no-running-bots";
            return 0;
        }

        if (!enabled) {
            lastSummary = "adaptive-disabled";
            return normalizedRequested;
        }

        MemorySnapshot snapshot;
        try {
            snapshot = memoryProbe.snapshot();
        } catch (RuntimeException exception) {
            lastSummary = "probe-unavailable";
            log.debug(
                    "[SCHEDULER MEMORY] Physical-memory probe failed. Keeping the existing adaptive cap of {} slot(s).",
                    adaptiveCap,
                    exception
            );
            return Math.min(normalizedRequested, adaptiveCap);
        }

        if (snapshot == null
                || snapshot.totalBytes() <= 0L
                || snapshot.freeBytes() < 0L
                || snapshot.freeBytes() > snapshot.totalBytes()) {
            lastSummary = "probe-unavailable";
            return Math.min(normalizedRequested, adaptiveCap);
        }

        double usedRatio =
                1.0d
                        - ((double) snapshot.freeBytes()
                        / (double) snapshot.totalBytes());

        int pressureCap = configuredMaxSlots;

        if (usedRatio >= EMERGENCY_USED_RATIO) {
            pressureCap = Math.min(
                    configuredMaxSlots,
                    EMERGENCY_CAP
            );
        } else if (usedRatio >= HIGH_USED_RATIO) {
            pressureCap = Math.min(
                    configuredMaxSlots,
                    HIGH_CAP
            );
        } else if (usedRatio >= ELEVATED_USED_RATIO) {
            pressureCap = Math.min(
                    configuredMaxSlots,
                    ELEVATED_CAP
            );
        }

        if (pressureCap < adaptiveCap) {
            int previousCap = adaptiveCap;
            adaptiveCap = pressureCap;
            healthySinceNanos = -1L;

            log.warn(
                    "[SCHEDULER MEMORY] Physical memory pressure is {}% (free {} GiB / total {} GiB). Reducing worker capacity {} -> {}. Active jobs are not killed; surplus slots retire after their current job.",
                    percent(usedRatio),
                    gib(snapshot.freeBytes()),
                    gib(snapshot.totalBytes()),
                    previousCap,
                    adaptiveCap
            );
        } else if (usedRatio <= RECOVERY_USED_RATIO
                && adaptiveCap < configuredMaxSlots) {
            long now = nanoTime.getAsLong();

            if (healthySinceNanos < 0L) {
                healthySinceNanos = now;
            } else if (now - healthySinceNanos
                    >= RECOVERY_HOLD_NANOS) {
                int previousCap = adaptiveCap;
                adaptiveCap = Math.min(
                        configuredMaxSlots,
                        adaptiveCap + 1
                );
                healthySinceNanos = now;

                log.info(
                        "[SCHEDULER MEMORY] Physical memory remained healthy at {}% used. Carefully restoring worker capacity {} -> {} (one slot per recovery window).",
                        percent(usedRatio),
                        previousCap,
                        adaptiveCap
                );
            }
        } else {
            healthySinceNanos = -1L;
        }

        lastSummary = String.format(
                Locale.ROOT,
                "%d%% used, %.1f GiB free, adaptiveCap=%d",
                percent(usedRatio),
                gib(snapshot.freeBytes()),
                adaptiveCap
        );

        return Math.min(
                normalizedRequested,
                adaptiveCap
        );
    }

    synchronized String lastSummary() {
        return lastSummary;
    }

    synchronized int adaptiveCap() {
        return adaptiveCap;
    }

    private static MemorySnapshot readSystemMemory() {
        java.lang.management.OperatingSystemMXBean bean =
                ManagementFactory.getOperatingSystemMXBean();

        if (!(bean instanceof OperatingSystemMXBean osBean)) {
            return null;
        }

        return new MemorySnapshot(
                osBean.getTotalMemorySize(),
                osBean.getFreeMemorySize()
        );
    }

    private static boolean readEnabled(String raw) {
        if (raw == null || raw.isBlank()) {
            return true;
        }

        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> {
                log.warn(
                        "Invalid {}='{}'. Adaptive memory slots remain enabled.",
                        ENABLED_ENV,
                        raw
                );
                yield true;
            }
        };
    }

    private static long percent(double ratio) {
        return Math.round(
                Math.max(0.0d, Math.min(1.0d, ratio))
                        * 100.0d
        );
    }

    private static double gib(long bytes) {
        return bytes / (1024.0d * 1024.0d * 1024.0d);
    }

    @FunctionalInterface
    interface MemoryProbe {
        MemorySnapshot snapshot();
    }

    record MemorySnapshot(
            long totalBytes,
            long freeBytes
    ) {
    }
}

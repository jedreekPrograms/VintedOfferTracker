package pl.flipbot.playwright.marketstats;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.util.Locale;

final class MarketStatsMemoryGuard {

    static final double MAX_USED_RATIO = 0.82d;
    static final long MIN_FREE_BYTES =
            5L * 1024L * 1024L * 1024L;

    private final MemoryProbe memoryProbe;
    private String lastSummary = "unmeasured";

    private MarketStatsMemoryGuard(MemoryProbe memoryProbe) {
        this.memoryProbe = memoryProbe;
    }

    static MarketStatsMemoryGuard systemDefault() {
        return new MarketStatsMemoryGuard(
                MarketStatsMemoryGuard::readSystemMemory
        );
    }

    static MarketStatsMemoryGuard forTest(MemoryProbe memoryProbe) {
        return new MarketStatsMemoryGuard(memoryProbe);
    }

    boolean shouldDeferNewBatch() {
        MemorySnapshot snapshot;

        try {
            snapshot = memoryProbe.snapshot();
        } catch (RuntimeException exception) {
            lastSummary = "probe-unavailable";
            return false;
        }

        if (snapshot == null
                || snapshot.totalBytes() <= 0L
                || snapshot.freeBytes() < 0L
                || snapshot.freeBytes() > snapshot.totalBytes()) {
            lastSummary = "probe-unavailable";
            return false;
        }

        double usedRatio =
                1.0d
                        - ((double) snapshot.freeBytes()
                        / (double) snapshot.totalBytes());

        lastSummary = String.format(
                Locale.ROOT,
                "%d%% used, %.1f GiB free",
                Math.round(usedRatio * 100.0d),
                gib(snapshot.freeBytes())
        );

        return usedRatio >= MAX_USED_RATIO
                || snapshot.freeBytes() < MIN_FREE_BYTES;
    }

    String lastSummary() {
        return lastSummary;
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

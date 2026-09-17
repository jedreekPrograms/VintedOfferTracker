package pl.flipbot.playwright.browser;

import com.sun.management.OperatingSystemMXBean;
import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Process-wide admission for scheduled browsers, including the market observer.
 * Existing jobs are never interrupted; permits survive until physical cleanup.
 * This controls local capacity, not account quotas or marketplace retry policy.
 */
@Slf4j
public final class BrowserCapacityController {
    static final long MIB = 1024L * 1024L;
    static final long LAUNCH_RESERVE_BYTES = 768L * MIB;
    static final long LAUNCH_SETTLE_MS = 10_000L;
    static final long START_SPACING_MS = 2_000L;
    static final long GROWTH_WINDOW_MS = 30_000L;
    private static final long SAMPLE_INTERVAL_MS = 1_000L;

    private final boolean adaptive;
    private final int maximum;
    private final Supplier<Resources> resources;
    private final LongSupplier clock;
    private final Map<Long, Long> active = new HashMap<>();
    private int target;
    private long sequence;
    private long nextStartAt;
    private long lastDeferralLogAt = Long.MIN_VALUE;
    private long nextReductionAt;
    private long growthNotBefore;
    private long healthySince = -1L;
    private long sampledAt = Long.MIN_VALUE;
    private Resources sampled = new Resources(-1L, -1L, -1.0);

    private static class Shared {
        private static final BrowserCapacityController INSTANCE = createFromEnvironment();
    }

    public static BrowserCapacityController shared() {
        return Shared.INSTANCE;
    }

    BrowserCapacityController(boolean adaptive, int maximum,
                              Supplier<Resources> resources, LongSupplier clock) {
        if (maximum < 1) throw new IllegalArgumentException("Browser capacity must be positive");
        this.adaptive = adaptive;
        this.maximum = maximum;
        this.target = Math.min(3, maximum);
        this.resources = resources;
        this.clock = clock;
    }

    private static BrowserCapacityController createFromEnvironment() {
        String configured = System.getenv("FLIPBOT_ADAPTIVE_CONCURRENCY");
        boolean adaptive = configured == null || !configured.trim().equalsIgnoreCase("false");
        int maximum = Math.max(1, Math.min(16, Runtime.getRuntime().availableProcessors()));
        String rawMaximum = System.getenv("FLIPBOT_MAX_BROWSER_JOBS");
        if (rawMaximum != null && !rawMaximum.isBlank()) {
            try {
                int parsed = Integer.parseInt(rawMaximum.trim());
                if (parsed < 1 || parsed > 100) throw new IllegalArgumentException();
                maximum = parsed;
            } catch (IllegalArgumentException ignored) {
                log.warn("[BROWSER CAPACITY] Invalid FLIPBOT_MAX_BROWSER_JOBS; using {}.", maximum);
            }
        }
        var bean = ManagementFactory.getOperatingSystemMXBean();
        Supplier<Resources> sample = () -> bean instanceof OperatingSystemMXBean os
                ? new Resources(os.getTotalMemorySize(), os.getFreeMemorySize(), os.getCpuLoad())
                : new Resources(-1L, -1L, -1.0);
        long originNanos = System.nanoTime();
        BrowserCapacityController result = new BrowserCapacityController(
                adaptive, maximum, sample, () -> (System.nanoTime() - originNanos) / 1_000_000L);
        log.info("[BROWSER CAPACITY] adaptive={}, initial={}, ceiling={}, logicalProcessors={}. "
                        + "Shared by scheduled bots and observer; minimum launch spacing={}ms.",
                adaptive, result.target, maximum, Runtime.getRuntime().availableProcessors(), START_SPACING_MS);
        return result;
    }

    public boolean adaptiveEnabled() { return adaptive; }
    public int maximumConcurrency() { return maximum; }
    public synchronized int targetConcurrency() { return target; }
    public synchronized int activeCount() { return active.size(); }

    public synchronized Permit acquire() {
        long now = clock.getAsLong();
        if (!adaptive) {
            // Explicit compatibility switch restores the old independent limits.
            long id = ++sequence;
            active.put(id, now);
            return new Permit(this, id);
        }
        Resources r = sample(now);
        boolean knownMemory = r.hasMemory();
        boolean knownCpu = Double.isFinite(r.cpuLoad()) && r.cpuLoad() >= 0 && r.cpuLoad() <= 1;
        long reserve = knownMemory ? Math.max(1536L * MIB, r.totalBytes() / 10L) : 0L;
        boolean pressure = (knownMemory && r.freeBytes() < reserve)
                || (knownCpu && r.cpuLoad() >= 0.90);
        boolean healthy = knownMemory && knownCpu
                && r.freeBytes() >= reserve + 2L * LAUNCH_RESERVE_BYTES
                && r.cpuLoad() < 0.75;

        if (!knownMemory || !knownCpu) {
            setTarget(Math.min(target, 3), "resource measurements unavailable");
        }
        if (pressure && now >= nextReductionAt) {
            setTarget(Math.max(1, target - 1), "memory/CPU pressure");
            nextReductionAt = now + LAUNCH_SETTLE_MS;
        }
        if (!healthy || now < growthNotBefore) {
            healthySince = -1L;
        } else if (healthySince < 0L) {
            healthySince = now;
        } else if (now - healthySince >= GROWTH_WINDOW_MS && active.size() >= target) {
            setTarget(Math.min(maximum, target + 1), "sustained headroom and queued demand");
            healthySince = now;
        }

        // Account for launches whose full resident footprint may not yet be
        // reflected by OS samples. This prevents a cold-start admission burst.
        long warming = active.values().stream()
                .filter(start -> now - start < LAUNCH_SETTLE_MS).count();
        long requiredFree = reserve + (warming + 1L) * LAUNCH_RESERVE_BYTES;
        if (pressure || active.size() >= target || now < nextStartAt
                || (knownMemory && r.freeBytes() < requiredFree)) {
            if (lastDeferralLogAt == Long.MIN_VALUE || now - lastDeferralLogAt >= 30_000L) {
                log.info("[BROWSER CAPACITY] New work waits. active={}/{}, ceiling={}, freeMiB={}, "
                                + "requiredFreeMiB={}, cpu={}; existing jobs continue.",
                        active.size(), target, maximum, knownMemory ? r.freeBytes() / MIB : -1,
                        knownMemory ? requiredFree / MIB : -1, r.cpuLoad());
                lastDeferralLogAt = now;
            }
            throw new BrowserCapacityUnavailableException(
                    "Waiting for local browser capacity: active=" + active.size()
                            + ", target=" + target + ", ceiling=" + maximum
                            + ", freeMiB=" + (knownMemory ? r.freeBytes() / MIB : -1));
        }
        long id = ++sequence;
        active.put(id, now);
        nextStartAt = now + START_SPACING_MS;
        log.info("[BROWSER CAPACITY] Admitted browser. active={}/{}, ceiling={}, freeMiB={}, cpu={}.",
                active.size(), target, maximum, knownMemory ? r.freeBytes() / MIB : -1, r.cpuLoad());
        return new Permit(this, id);
    }

    /** Feedback only reduces future parallelism; existing account cooldowns remain authoritative. */
    public synchronized void marketplaceBackoff(long durationMillis) {
        if (!adaptive) return;
        setTarget(Math.max(1, target / 2), "marketplace backoff");
        growthNotBefore = Math.max(growthNotBefore,
                clock.getAsLong() + Math.max(600_000L, durationMillis));
        healthySince = -1L;
    }

    private Resources sample(long now) {
        if (sampledAt == Long.MIN_VALUE || now - sampledAt >= SAMPLE_INTERVAL_MS) {
            try {
                Resources next = resources.get();
                sampled = next == null ? new Resources(-1L, -1L, -1.0) : next;
            } catch (RuntimeException ignored) {
                sampled = new Resources(-1L, -1L, -1.0);
            }
            sampledAt = now;
        }
        return sampled;
    }

    private void setTarget(int value, String reason) {
        if (value != target) {
            log.info("[BROWSER CAPACITY] Target {} -> {} (ceiling={}). reason={}; running jobs finish normally.",
                    target, value, maximum, reason);
            target = value;
        }
    }

    private synchronized void release(long id) { active.remove(id); }

    record Resources(long totalBytes, long freeBytes, double cpuLoad) {
        boolean hasMemory() { return totalBytes > 0 && freeBytes >= 0 && freeBytes <= totalBytes; }
    }

    public static final class Permit implements AutoCloseable {
        private final BrowserCapacityController owner;
        private final long id;
        private Permit(BrowserCapacityController owner, long id) { this.owner = owner; this.id = id; }
        @Override public void close() { owner.release(id); }
    }
}

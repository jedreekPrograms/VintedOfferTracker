package pl.flipbot.playwright.marketstats;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarketStatsMemoryGuardTest {

    private static final long GIB = 1024L * 1024L * 1024L;

    @Test
    public void healthyMemoryAllowsObserverBatch() {
        MarketStatsMemoryGuard guard = MarketStatsMemoryGuard.forTest(
                () -> new MarketStatsMemoryGuard.MemorySnapshot(
                        32L * GIB,
                        9L * GIB
                )
        );

        assertFalse(guard.shouldDeferNewBatch());
    }

    @Test
    public void highUsedRatioDefersObserverBatch() {
        MarketStatsMemoryGuard guard = MarketStatsMemoryGuard.forTest(
                () -> new MarketStatsMemoryGuard.MemorySnapshot(
                        32L * GIB,
                        4L * GIB
                )
        );

        assertTrue(guard.shouldDeferNewBatch());
    }

    @Test
    public void sixteenGigabyteHostIsNotDeferredAtSeventyFivePercentUsage() {
        MarketStatsMemoryGuard guard = MarketStatsMemoryGuard.forTest(
                () -> new MarketStatsMemoryGuard.MemorySnapshot(
                        16L * GIB,
                        4L * GIB
                )
        );

        assertFalse(guard.shouldDeferNewBatch());
    }

    @Test
    public void criticallyLowAbsoluteFreeMemoryStillDefers() {
        MarketStatsMemoryGuard guard = MarketStatsMemoryGuard.forTest(
                () -> new MarketStatsMemoryGuard.MemorySnapshot(
                        10L * GIB,
                        (19L * GIB) / 10L
                )
        );

        assertTrue(guard.shouldDeferNewBatch());
    }
}

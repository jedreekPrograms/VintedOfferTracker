package pl.flipbot.playwright.marketstats;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarketStatsRuntimeConfigTest {

    @Test
    public void inheritsVisibleSchedulerWhenObserverOverrideIsMissing() {
        assertFalse(
                MarketStatsRuntimeConfig.resolveObserverHeadless(
                        null,
                        "false"
                )
        );
    }

    @Test
    public void inheritsHeadlessSchedulerWhenObserverOverrideIsMissing() {
        assertTrue(
                MarketStatsRuntimeConfig.resolveObserverHeadless(
                        null,
                        "true"
                )
        );
    }

    @Test
    public void explicitObserverOverrideWinsOverSchedulerSetting() {
        assertTrue(
                MarketStatsRuntimeConfig.resolveObserverHeadless(
                        "true",
                        "false"
                )
        );

        assertFalse(
                MarketStatsRuntimeConfig.resolveObserverHeadless(
                        "false",
                        "true"
                )
        );
    }

    @Test
    public void defaultsToHeadlessWhenNeitherSettingExists() {
        assertTrue(
                MarketStatsRuntimeConfig.resolveObserverHeadless(
                        null,
                        null
                )
        );
    }

    @Test
    public void acceptsCommonBooleanAliases() {
        assertFalse(
                MarketStatsRuntimeConfig.resolveObserverHeadless(
                        null,
                        "off"
                )
        );
        assertTrue(
                MarketStatsRuntimeConfig.resolveObserverHeadless(
                        "yes",
                        "off"
                )
        );
    }

    @Test
    public void defaultsToFifteenMinuteCooldown() {
        assertEquals(
                15L,
                MarketStatsRuntimeConfig.resolveRefreshCooldownMinutes(
                        null,
                        null
                )
        );
    }

    @Test
    public void explicitMinuteCooldownWinsOverLegacyHours() {
        assertEquals(
                25L,
                MarketStatsRuntimeConfig.resolveRefreshCooldownMinutes(
                        "25",
                        "24"
                )
        );
    }

    @Test
    public void legacyHoursRemainBackwardCompatible() {
        assertEquals(
                120L,
                MarketStatsRuntimeConfig.resolveRefreshCooldownMinutes(
                        null,
                        "2"
                )
        );
    }

    @Test
    public void refreshCooldownIsClampedToSafeBounds() {
        assertEquals(
                5L,
                MarketStatsRuntimeConfig.resolveRefreshCooldownMinutes(
                        "1",
                        null
                )
        );
        assertEquals(
                1_440L,
                MarketStatsRuntimeConfig.resolveRefreshCooldownMinutes(
                        "99999",
                        null
                )
        );
    }

    @Test
    public void marketStatsBrowserDefaultsToThreeTargetsBeforeRecycle() {
        assertEquals(
                3,
                MarketStatsRuntimeConfig.resolveBrowserRecycleTargetCount(null)
        );
        assertEquals(
                3,
                MarketStatsRuntimeConfig.resolveBrowserRecycleTargetCount("bad")
        );
    }

    @Test
    public void marketStatsBrowserRecycleCountIsConfigurableAndBounded() {
        assertEquals(
                5,
                MarketStatsRuntimeConfig.resolveBrowserRecycleTargetCount("5")
        );
        assertEquals(
                1,
                MarketStatsRuntimeConfig.resolveBrowserRecycleTargetCount("0")
        );
        assertEquals(
                50,
                MarketStatsRuntimeConfig.resolveBrowserRecycleTargetCount("999")
        );
    }
}

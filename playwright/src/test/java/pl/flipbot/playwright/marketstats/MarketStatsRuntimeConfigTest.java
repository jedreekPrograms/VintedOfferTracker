package pl.flipbot.playwright.marketstats;

import org.junit.Test;

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
}

package pl.flipbot.playwright.worker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScheduledActionLimitConfigTest {

    @Test
    public void productionUsesConfiguredThroughput() {
        ScheduledActionLimitConfig config =
                new ScheduledActionLimitConfig(4, 2);

        assertEquals(4, config.effectiveMaxRealOffers(true));
        assertEquals(2, config.effectiveMaxRealNextSteps(true));
    }

    @Test
    public void controlledModeAlwaysStaysAtOneAction() {
        ScheduledActionLimitConfig config =
                new ScheduledActionLimitConfig(5, 5);

        assertEquals(1, config.effectiveMaxRealOffers(false));
        assertEquals(1, config.effectiveMaxRealNextSteps(false));
    }

    @Test
    public void missingFirstOfferLimitDefaultsToThree() {
        assertEquals(
                3,
                ScheduledActionLimitConfig.parseLimit(
                        ScheduledActionLimitConfig.MAX_REAL_OFFERS_ENV,
                        null,
                        3
                )
        );
    }

    @Test
    public void invalidLimitFallsBackToDefault() {
        assertEquals(
                3,
                ScheduledActionLimitConfig.parseLimit(
                        ScheduledActionLimitConfig.MAX_REAL_OFFERS_ENV,
                        "100",
                        3
                )
        );
    }

    @Test
    public void upperBoundaryFiveIsAccepted() {
        assertEquals(
                5,
                ScheduledActionLimitConfig.parseLimit(
                        ScheduledActionLimitConfig.MAX_REAL_OFFERS_ENV,
                        "5",
                        3
                )
        );
    }
}

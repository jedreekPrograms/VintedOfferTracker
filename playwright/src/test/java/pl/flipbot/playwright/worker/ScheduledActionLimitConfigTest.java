package pl.flipbot.playwright.worker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ScheduledActionLimitConfigTest {

    @Test
    public void productionUsesConfiguredOperatorThrottle() {
        ScheduledActionLimitConfig config =
                new ScheduledActionLimitConfig(4, 2);

        assertEquals(4, config.effectiveMaxRealOffers(true));
        assertEquals(2, config.effectiveMaxRealNextSteps(true));
    }

    @Test
    public void productionCanBeUnboundedAtRuntimeLayer() {
        ScheduledActionLimitConfig config =
                new ScheduledActionLimitConfig(Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertEquals(Integer.MAX_VALUE, config.effectiveMaxRealOffers(true));
        assertEquals(Integer.MAX_VALUE, config.effectiveMaxRealNextSteps(true));
    }

    @Test
    public void controlledModeAlwaysStaysAtOneAction() {
        ScheduledActionLimitConfig config =
                new ScheduledActionLimitConfig(Integer.MAX_VALUE, Integer.MAX_VALUE);

        assertEquals(1, config.effectiveMaxRealOffers(false));
        assertEquals(1, config.effectiveMaxRealNextSteps(false));
    }

    @Test
    public void missingLimitUsesProvidedBackendControlledDefault() {
        assertEquals(
                Integer.MAX_VALUE,
                ScheduledActionLimitConfig.parseLimit(
                        ScheduledActionLimitConfig.MAX_REAL_OFFERS_ENV,
                        null,
                        Integer.MAX_VALUE
                )
        );
    }

    @Test
    public void invalidNonPositiveLimitFallsBackToDefault() {
        assertEquals(
                Integer.MAX_VALUE,
                ScheduledActionLimitConfig.parseLimit(
                        ScheduledActionLimitConfig.MAX_REAL_OFFERS_ENV,
                        "0",
                        Integer.MAX_VALUE
                )
        );
    }

    @Test
    public void explicitLargePositiveThrottleIsAccepted() {
        assertEquals(
                100,
                ScheduledActionLimitConfig.parseLimit(
                        ScheduledActionLimitConfig.MAX_REAL_OFFERS_ENV,
                        "100",
                        Integer.MAX_VALUE
                )
        );
    }
}

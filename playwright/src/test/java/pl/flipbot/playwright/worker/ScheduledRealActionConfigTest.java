package pl.flipbot.playwright.worker;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ScheduledRealActionConfigTest {

    @Test
    public void controlledModeKeepsOneShotAndExplicitAllowlist() {
        ScheduledRealActionConfig config =
                config(
                        true,
                        true,
                        Set.of(3L),
                        true,
                        false,
                        false,
                        false,
                        false
                );

        assertFalse(config.productionModeEnabled());
        assertTrue(config.firstOfferOneShotTestModeEnabled());
        assertTrue(config.realOffersEnabledFor(3L));
        assertTrue(config.realNextStepsEnabledFor(3L));
        assertFalse(config.realOffersEnabledFor(4L));
    }

    @Test
    public void productionModeCanArmAllRunningBots() {
        ScheduledRealActionConfig config =
                config(
                        true,
                        true,
                        Set.of(),
                        true,
                        false,
                        true,
                        true,
                        true
                );

        assertTrue(config.productionModeEnabled());
        assertFalse(config.firstOfferOneShotTestModeEnabled());
        assertTrue(config.realOffersEnabledFor(1L));
        assertTrue(config.realNextStepsEnabledFor(999L));
        assertFalse(config.realOffersEnabledFor(0L));
        assertFalse(config.realOffersEnabledFor(null));
    }

    @Test
    public void productionModeCanStillUseExplicitAllowlist() {
        ScheduledRealActionConfig config =
                config(
                        true,
                        true,
                        Set.of(3L, 7L),
                        true,
                        false,
                        true,
                        true,
                        false
                );

        assertTrue(config.productionModeEnabled());
        assertFalse(config.firstOfferOneShotTestModeEnabled());
        assertTrue(config.realOffersEnabledFor(3L));
        assertTrue(config.realOffersEnabledFor(7L));
        assertFalse(config.realOffersEnabledFor(8L));
    }

    @Test
    public void productionModeFailsClosedWithoutSecondConfirmation() {
        ScheduledRealActionConfig config =
                config(
                        true,
                        true,
                        Set.of(),
                        true,
                        false,
                        true,
                        false,
                        true
                );

        assertFalse(config.productionModeEnabled());
        assertTrue(config.firstOfferOneShotTestModeEnabled());
        assertFalse(config.realOffersEnabledFor(3L));
        assertFalse(config.realNextStepsEnabledFor(3L));
    }

    @Test
    public void allowAllRunningBotsFailsClosedOutsideProductionMode() {
        ScheduledRealActionConfig config =
                config(
                        true,
                        true,
                        Set.of(),
                        true,
                        false,
                        false,
                        false,
                        true
                );

        assertFalse(config.productionModeEnabled());
        assertTrue(config.firstOfferOneShotTestModeEnabled());
        assertFalse(config.realOffersEnabledFor(3L));
    }

    @Test
    public void preflightNeverEnablesRealSubmission() {
        ScheduledRealActionConfig config =
                config(
                        true,
                        true,
                        Set.of(),
                        true,
                        true,
                        true,
                        true,
                        true
                );

        assertFalse(config.productionModeEnabled());
        assertTrue(config.realOffersRequestedFor(3L));
        assertTrue(config.realNextStepsRequestedFor(3L));
        assertFalse(config.realOffersEnabledFor(3L));
        assertFalse(config.realNextStepsEnabledFor(3L));
    }

    private ScheduledRealActionConfig config(
            boolean realOffersRequested,
            boolean realNextStepsRequested,
            Set<Long> allowedBotIds,
            boolean confirmationValid,
            boolean preflightOnly,
            boolean productionModeRequested,
            boolean productionConfirmationValid,
            boolean allowAllRunningBotsRequested
    ) {
        return new ScheduledRealActionConfig(
                realOffersRequested,
                realNextStepsRequested,
                allowedBotIds,
                confirmationValid,
                preflightOnly,
                productionModeRequested,
                productionConfirmationValid,
                allowAllRunningBotsRequested
        );
    }
}

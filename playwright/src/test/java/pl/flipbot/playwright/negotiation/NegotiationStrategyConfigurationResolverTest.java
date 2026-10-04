package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.model.NegotiationStrategySnapshotDto;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class NegotiationStrategyConfigurationResolverTest {

    @Test
    public void oldConversationKeepsPinnedLadderWhenLiveStrategyChanges() {
        BotConfigurationDto current = configuration(
                "1600.00",
                "1350.00",
                "nowa wiadomość"
        );
        NegotiationStrategySnapshotDto snapshot = snapshot(
                3,
                "1450.00",
                "1200.00",
                "stara wiadomość"
        );

        BotConfigurationDto effective =
                NegotiationStrategyConfigurationResolver.resolve(
                        current,
                        snapshot
                );

        assertEquals(
                0,
                new BigDecimal("1450.00").compareTo(
                        effective.getMaxAutomaticOffer()
                )
        );
        assertEquals(
                0,
                new BigDecimal("1200.00").compareTo(
                        effective.getNegotiationSteps().get(0).getOfferPrice()
                )
        );
        assertEquals(
                "stara wiadomość",
                effective.getNegotiationSteps().get(0).getMessage()
        );
    }

    @Test
    public void loweringCurrentCapActsAsSafetyBrakeForPinnedAdaptiveConversation() {
        BotConfigurationDto current = configuration(
                "1350.00",
                "1300.00",
                "v2"
        );
        NegotiationStrategySnapshotDto snapshot = snapshot(
                1,
                "1450.00",
                "1200.00",
                "v1"
        );

        BotConfigurationDto effective =
                NegotiationStrategyConfigurationResolver.resolve(
                        current,
                        snapshot
                );

        assertEquals(
                0,
                new BigDecimal("1350.00").compareTo(
                        effective.getMaxAutomaticOffer()
                )
        );
        assertEquals("v1", effective.getNegotiationSteps().get(0).getMessage());
    }

    @Test
    public void missingLegacySnapshotFallsBackToCurrentConfiguration() {
        BotConfigurationDto current = configuration(
                "1450.00",
                "1200.00",
                "current"
        );

        assertSame(
                current,
                NegotiationStrategyConfigurationResolver.resolve(
                        current,
                        null
                )
        );
    }

    private BotConfigurationDto configuration(
            String cap,
            String firstOffer,
            String message
    ) {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setAutoRaiseOfferToVintedMinimum(true);
        configuration.setMaxAutomaticOffer(new BigDecimal(cap));
        configuration.setNegotiationSteps(
                List.of(step(firstOffer, message))
        );
        return configuration;
    }

    private NegotiationStrategySnapshotDto snapshot(
            int version,
            String cap,
            String firstOffer,
            String message
    ) {
        NegotiationStrategySnapshotDto snapshot =
                new NegotiationStrategySnapshotDto();
        snapshot.setVersion(version);
        snapshot.setAutoRaiseOfferToVintedMinimum(true);
        snapshot.setMaxAutomaticOffer(new BigDecimal(cap));
        snapshot.setNegotiationSteps(
                List.of(step(firstOffer, message))
        );
        return snapshot;
    }

    private NegotiationStepDto step(
            String offer,
            String message
    ) {
        NegotiationStepDto step = new NegotiationStepDto();
        step.setStepNumber(1);
        step.setOfferPrice(new BigDecimal(offer));
        step.setMaxAcceptedCounterOffer(new BigDecimal("1400.00"));
        step.setMessage(message);
        return step;
    }
}

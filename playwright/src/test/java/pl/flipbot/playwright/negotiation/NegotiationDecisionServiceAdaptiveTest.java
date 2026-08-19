package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NegotiationDecisionServiceAdaptiveTest {

    private final NegotiationDecisionService service =
            new NegotiationDecisionService();

    @Test
    public void rejectionUsesScaledNextStepFromActualFirstOffer() {
        BotConfigurationDto configuration = adaptiveConfiguration("1500.00");
        ListingResponseDto listing = negotiatingListing("1250.00", 1);

        NegotiationDecision decision = service.decide(
                listing,
                NegotiationConversationSnapshot.rejected("Odrzucono"),
                configuration
        );

        assertEquals(
                NegotiationDecisionType.SEND_NEXT_STEP,
                decision.type()
        );
        assertNotNull(decision.nextStep());
        assertEquals(2, decision.nextStep().getStepNumber().intValue());
        assertEquals(
                0,
                new BigDecimal("1390.00").compareTo(
                        decision.nextStep().getOfferPrice()
                )
        );
        assertEquals("second message", decision.nextStep().getMessage());
    }

    @Test
    public void sellerCounterOfferUsesScaledAcceptanceThreshold() {
        BotConfigurationDto configuration = adaptiveConfiguration("1500.00");
        ListingResponseDto listing = negotiatingListing("1250.00", 1);

        NegotiationDecision decision = service.decide(
                listing,
                NegotiationConversationSnapshot.sellerCounterOffer(
                        new BigDecimal("1310.00")
                ),
                configuration
        );

        assertEquals(
                NegotiationDecisionType.MARK_ACTION_REQUIRED,
                decision.type()
        );
    }

    @Test
    public void counterOfferAboveScaledThresholdEscalatesWithScaledPrice() {
        BotConfigurationDto configuration = adaptiveConfiguration("1500.00");
        ListingResponseDto listing = negotiatingListing("1250.00", 1);

        NegotiationDecision decision = service.decide(
                listing,
                NegotiationConversationSnapshot.sellerCounterOffer(
                        new BigDecimal("1350.00")
                ),
                configuration
        );

        assertEquals(
                NegotiationDecisionType.SEND_NEXT_STEP,
                decision.type()
        );
        assertEquals(
                0,
                new BigDecimal("1390.00").compareTo(
                        decision.nextStep().getOfferPrice()
                )
        );
    }

    @Test
    public void globalCapStopsNextAutomaticEscalation() {
        BotConfigurationDto configuration = adaptiveConfiguration("1350.00");
        ListingResponseDto listing = negotiatingListing("1250.00", 1);

        NegotiationDecision decision = service.decide(
                listing,
                NegotiationConversationSnapshot.rejected("Odrzucono"),
                configuration
        );

        assertEquals(
                NegotiationDecisionType.MARK_REJECTED,
                decision.type()
        );
    }

    @Test
    public void loweredCapBelowAlreadySentOfferStopsFutureEscalation() {
        BotConfigurationDto configuration = adaptiveConfiguration("1000.00");
        ListingResponseDto listing = negotiatingListing("1390.00", 1);

        NegotiationDecision decision = service.decide(
                listing,
                NegotiationConversationSnapshot.rejected("Odrzucono"),
                configuration
        );

        assertEquals(
                NegotiationDecisionType.MARK_REJECTED,
                decision.type()
        );
    }

    @Test
    public void staticModeKeepsConfiguredNextPrice() {
        BotConfigurationDto configuration = adaptiveConfiguration("1500.00");
        configuration.setAutoRaiseOfferToVintedMinimum(false);
        configuration.setMaxAutomaticOffer(null);

        ListingResponseDto listing = negotiatingListing("900.00", 1);

        NegotiationDecision decision = service.decide(
                listing,
                NegotiationConversationSnapshot.rejected("Odrzucono"),
                configuration
        );

        assertEquals(
                NegotiationDecisionType.SEND_NEXT_STEP,
                decision.type()
        );
        assertEquals(
                0,
                new BigDecimal("1000.00").compareTo(
                        decision.nextStep().getOfferPrice()
                )
        );
    }

    private BotConfigurationDto adaptiveConfiguration(String cap) {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setAutoRaiseOfferToVintedMinimum(true);
        configuration.setMaxAutomaticOffer(new BigDecimal(cap));
        configuration.setNegotiationSteps(
                List.of(
                        step(1, "900.00", "950.00", "first message"),
                        step(2, "1000.00", "1050.00", "second message"),
                        step(3, "1050.00", "1100.00", "third message")
                )
        );
        return configuration;
    }

    private NegotiationStepDto step(
            int number,
            String offer,
            String acceptedCounter,
            String message
    ) {
        NegotiationStepDto step = new NegotiationStepDto();
        step.setStepNumber(number);
        step.setOfferPrice(new BigDecimal(offer));
        step.setMaxAcceptedCounterOffer(new BigDecimal(acceptedCounter));
        step.setMessage(message);
        return step;
    }

    private ListingResponseDto negotiatingListing(
            String currentPrice,
            int currentStep
    ) {
        return new ListingResponseDto(
                100L,
                "9700000000",
                "Samsung Galaxy S25",
                "https://www.vinted.pl/items/9700000000-samsung-galaxy-s25",
                new BigDecimal("2000.00"),
                new BigDecimal(currentPrice),
                currentStep,
                true,
                "12345",
                "https://www.vinted.pl/inbox/12345",
                "NEGOTIATING",
                null
        );
    }
}

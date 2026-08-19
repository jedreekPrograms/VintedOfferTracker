package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationReactionAction;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.model.SellerCounterOfferRuleDto;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class NegotiationResponsePolicyTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-08-19T10:00:00Z"),
            ZoneId.of("Europe/Warsaw")
    );

    private final NegotiationDecisionService service =
            new NegotiationDecisionService(FIXED_CLOCK);

    @Test
    public void firstStepRejectionCanAdvanceImmediately() {
        BotConfigurationDto configuration = configuration();
        configuration.getNegotiationSteps().get(0)
                .setRejectionAction(NegotiationReactionAction.NEXT_STEP_NOW);

        NegotiationDecision decision = service.decide(
                listing(1, "900", null, null),
                NegotiationConversationSnapshot.rejected("Odrzucono"),
                configuration
        );

        assertEquals(NegotiationDecisionType.SEND_NEXT_STEP, decision.type());
        assertEquals(2, decision.nextStep().getStepNumber().intValue());
    }

    @Test
    public void rejectionWaitsUntilConfiguredSixHoursElapsed() {
        BotConfigurationDto configuration = configuration();
        NegotiationStepDto first = configuration.getNegotiationSteps().get(0);
        first.setRejectionAction(NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP);
        first.setRejectionWaitHours(6);

        NegotiationDecision decision = service.decide(
                listing(
                        1,
                        "900",
                        "REJECTED:1",
                        "2026-08-19T07:00:00"
                ),
                NegotiationConversationSnapshot.rejected("Odrzucono"),
                configuration
        );

        assertEquals(NegotiationDecisionType.WAIT, decision.type());
    }

    @Test
    public void rejectionAdvancesAfterConfiguredWaitElapsed() {
        BotConfigurationDto configuration = configuration();
        NegotiationStepDto first = configuration.getNegotiationSteps().get(0);
        first.setRejectionAction(NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP);
        first.setRejectionWaitHours(6);

        NegotiationDecision decision = service.decide(
                listing(
                        1,
                        "900",
                        "REJECTED:1",
                        "2026-08-19T05:30:00"
                ),
                NegotiationConversationSnapshot.rejected("Odrzucono"),
                configuration
        );

        assertEquals(NegotiationDecisionType.SEND_NEXT_STEP, decision.type());
    }

    @Test
    public void tenPercentSellerDiscountUsesTwoHourWaitRule() {
        BotConfigurationDto configuration = configuration();

        NegotiationDecision decision = service.decide(
                listing(
                        1,
                        "900",
                        "COUNTER:1:1800",
                        "2026-08-19T10:30:00"
                ),
                NegotiationConversationSnapshot.sellerCounterOffer(
                        new BigDecimal("1800")
                ),
                configuration
        );

        assertEquals(NegotiationDecisionType.WAIT, decision.type());
    }

    @Test
    public void tenPercentSellerDiscountAdvancesAfterTwoHours() {
        BotConfigurationDto configuration = configuration();

        NegotiationDecision decision = service.decide(
                listing(
                        1,
                        "900",
                        "COUNTER:1:1800",
                        "2026-08-19T09:30:00"
                ),
                NegotiationConversationSnapshot.sellerCounterOffer(
                        new BigDecimal("1800")
                ),
                configuration
        );

        assertEquals(NegotiationDecisionType.SEND_NEXT_STEP, decision.type());
    }

    @Test
    public void fifteenPercentSellerDiscountUsesHighestMatchingImmediateRule() {
        BotConfigurationDto configuration = configuration();

        NegotiationDecision decision = service.decide(
                listing(1, "900", null, null),
                NegotiationConversationSnapshot.sellerCounterOffer(
                        new BigDecimal("1700")
                ),
                configuration
        );

        assertEquals(NegotiationDecisionType.SEND_NEXT_STEP, decision.type());
    }

    @Test
    public void discountIsCalculatedFromOriginalListingPriceNotCurrentOffer() {
        BotConfigurationDto configuration = configuration();

        NegotiationDecision decision = service.decide(
                listing(1, "1250", null, null),
                NegotiationConversationSnapshot.sellerCounterOffer(
                        new BigDecimal("1700")
                ),
                configuration
        );

        assertEquals(NegotiationDecisionType.SEND_NEXT_STEP, decision.type());
    }

    @Test
    public void sellerPriceBetterThanPlannedNextOfferBecomesActionRequired() {
        BotConfigurationDto configuration = configuration();

        NegotiationDecision decision = service.decide(
                listing(1, "900", null, null),
                NegotiationConversationSnapshot.sellerCounterOffer(
                        new BigDecimal("980")
                ),
                configuration
        );

        assertEquals(
                NegotiationDecisionType.MARK_ACTION_REQUIRED,
                decision.type()
        );
    }

    @Test
    public void delayedRuleFailsClosedUntilStableFingerprintTimestampIsPersisted() {
        BotConfigurationDto configuration = configuration();

        NegotiationDecision decision = service.decide(
                listing(1, "900", "COUNTER:1:1750", "2026-08-19T01:00:00"),
                NegotiationConversationSnapshot.sellerCounterOffer(
                        new BigDecimal("1800")
                ),
                configuration
        );

        assertEquals(NegotiationDecisionType.WAIT, decision.type());
    }

    private BotConfigurationDto configuration() {
        NegotiationStepDto step1 = step(1, "900", "950");
        step1.setRejectionAction(NegotiationReactionAction.NEXT_STEP_NOW);
        step1.setCounterOfferDefaultAction(
                NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
        );
        step1.setCounterOfferDefaultWaitHours(6);
        step1.setCounterOfferRules(List.of(
                rule("10", NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 2),
                rule("15", NegotiationReactionAction.NEXT_STEP_NOW, null)
        ));

        NegotiationStepDto step2 = step(2, "1000", "1050");
        step2.setRejectionAction(NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP);
        step2.setRejectionWaitHours(6);
        step2.setCounterOfferDefaultAction(
                NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
        );
        step2.setCounterOfferDefaultWaitHours(6);
        step2.setCounterOfferRules(step1.getCounterOfferRules());

        NegotiationStepDto step3 = step(3, "1100", "1150");

        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setAutoRaiseOfferToVintedMinimum(false);
        configuration.setNegotiationSteps(List.of(step1, step2, step3));
        return configuration;
    }

    private NegotiationStepDto step(
            int number,
            String offer,
            String acceptedCounter
    ) {
        NegotiationStepDto step = new NegotiationStepDto();
        step.setStepNumber(number);
        step.setOfferPrice(new BigDecimal(offer));
        step.setMaxAcceptedCounterOffer(new BigDecimal(acceptedCounter));
        step.setMessage("message " + number);
        return step;
    }

    private SellerCounterOfferRuleDto rule(
            String discount,
            NegotiationReactionAction action,
            Integer waitHours
    ) {
        SellerCounterOfferRuleDto rule = new SellerCounterOfferRuleDto();
        rule.setMinimumDiscountPercent(new BigDecimal(discount));
        rule.setAction(action);
        rule.setWaitHours(waitHours);
        return rule;
    }

    private ListingResponseDto listing(
            int currentStep,
            String currentPrice,
            String fingerprint,
            String detectedAt
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
                null,
                "2026-08-19T08:00:00",
                null,
                null,
                fingerprint,
                detectedAt
        );
    }
}

package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationReactionAction;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PendingNegotiationPolicyTest {

    private final PendingNegotiationPolicy policy =
            new PendingNegotiationPolicy(
                    Clock.fixed(
                            Instant.parse("2026-01-03T10:00:00Z"),
                            ZoneOffset.UTC
                    )
            );

    @Test
    public void nonFinalNoResponseAdvancesAfterConfiguredRejectionWait() {
        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-03T03:59:59",
                        null,
                        null,
                        2
                ),
                ConversationActivitySnapshot.unavailable(),
                fiveStepConfiguration()
        );

        assertEquals(
                PendingNegotiationDecision.Action.SEND_NEXT_STEP,
                decision.action()
        );
        assertEquals(3, decision.nextStep().getStepNumber().intValue());
        assertTrue(decision.reason().contains("no-response timeout of 6h"));
    }

    @Test
    public void nonFinalNoResponseWaitsBeforeConfiguredRejectionWait() {
        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-03T04:00:01",
                        null,
                        null,
                        2
                ),
                ConversationActivitySnapshot.unavailable(),
                fiveStepConfiguration()
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(decision.reason().contains("no-response timeout is 6h"));
    }

    @Test
    public void readPendingOfferUsesSameConfiguredWaitAsRejection() {
        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-03T00:00:00",
                        null,
                        "2026-01-03T05:00:00",
                        2
                ),
                new ConversationActivitySnapshot(
                        true,
                        true,
                        false,
                        null,
                        null,
                        true
                ),
                fiveStepConfiguration()
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(
                decision.reason().contains(
                        "treated like a rejection for timing"
                )
        );
        assertTrue(decision.reason().contains("wait 6h after rejection"));
    }

    @Test
    public void readPendingOfferAdvancesWhenRejectionWaitElapsed() {
        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-03T00:00:00",
                        null,
                        "2026-01-03T03:59:59",
                        2
                ),
                new ConversationActivitySnapshot(
                        true,
                        true,
                        false,
                        null,
                        null,
                        true
                ),
                fiveStepConfiguration()
        );

        assertEquals(
                PendingNegotiationDecision.Action.SEND_NEXT_STEP,
                decision.action()
        );
        assertEquals(3, decision.nextStep().getStepNumber().intValue());
    }

    @Test
    public void readPendingOfferUsesImmediateRejectionPolicyImmediately() {
        BotConfigurationDto configuration = fiveStepConfiguration();
        NegotiationStepDto step2 =
                configuration.getNegotiationSteps().get(1);
        step2.setRejectionAction(
                NegotiationReactionAction.NEXT_STEP_NOW
        );
        step2.setRejectionWaitHours(null);

        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-03T09:00:00",
                        null,
                        "2026-01-03T09:59:59",
                        2
                ),
                new ConversationActivitySnapshot(
                        true,
                        true,
                        false,
                        null,
                        null,
                        true
                ),
                configuration
        );

        assertEquals(
                PendingNegotiationDecision.Action.SEND_NEXT_STEP,
                decision.action()
        );
        assertEquals(3, decision.nextStep().getStepNumber().intValue());
        assertTrue(decision.reason().contains("send the next step immediately"));
    }

    @Test
    public void sellerChatMessageUsesSameConfiguredWaitAsRejection() {
        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-03T00:00:00",
                        "2026-01-03T05:00:00",
                        null,
                        2
                ),
                new ConversationActivitySnapshot(
                        true,
                        true,
                        true,
                        "Czy cena aktualna?",
                        LocalDateTime.parse("2026-01-03T05:00:00"),
                        false
                ),
                fiveStepConfiguration()
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(
                decision.reason().contains(
                        "treated like a rejection for timing"
                )
        );
        assertTrue(decision.reason().contains("wait 6h after rejection"));
    }

    @Test
    public void sellerChatMessageAdvancesWhenRejectionWaitElapsed() {
        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-03T00:00:00",
                        "2026-01-03T03:59:59",
                        null,
                        2
                ),
                new ConversationActivitySnapshot(
                        true,
                        true,
                        true,
                        "Czy cena aktualna?",
                        LocalDateTime.parse("2026-01-03T03:59:59"),
                        false
                ),
                fiveStepConfiguration()
        );

        assertEquals(
                PendingNegotiationDecision.Action.SEND_NEXT_STEP,
                decision.action()
        );
        assertEquals(3, decision.nextStep().getStepNumber().intValue());
    }

    @Test
    public void sellerChatMessageUsesImmediateRejectionPolicyImmediately() {
        BotConfigurationDto configuration = fiveStepConfiguration();
        NegotiationStepDto step2 =
                configuration.getNegotiationSteps().get(1);
        step2.setRejectionAction(
                NegotiationReactionAction.NEXT_STEP_NOW
        );
        step2.setRejectionWaitHours(null);

        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-03T09:00:00",
                        "2026-01-03T09:59:00",
                        null,
                        2
                ),
                new ConversationActivitySnapshot(
                        true,
                        true,
                        true,
                        "Dzień dobry",
                        LocalDateTime.parse("2026-01-03T09:59:00"),
                        false
                ),
                configuration
        );

        assertEquals(
                PendingNegotiationDecision.Action.SEND_NEXT_STEP,
                decision.action()
        );
        assertEquals(3, decision.nextStep().getStepNumber().intValue());
    }

    @Test
    public void immediateRejectionPolicyUsesTwelveHourFallbackOnlyWhenThereIsNoSellerReaction() {
        BotConfigurationDto configuration = fiveStepConfiguration();
        NegotiationStepDto step2 =
                configuration.getNegotiationSteps().get(1);
        step2.setRejectionAction(
                NegotiationReactionAction.NEXT_STEP_NOW
        );
        step2.setRejectionWaitHours(null);

        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-02T21:59:59",
                        null,
                        null,
                        2
                ),
                ConversationActivitySnapshot.unavailable(),
                configuration
        );

        assertEquals(
                PendingNegotiationDecision.Action.SEND_NEXT_STEP,
                decision.action()
        );
        assertTrue(decision.reason().contains("no-response timeout of 12h"));
    }

    @Test
    public void finalUntouchedPendingOfferExpiresAfter48Hours() {
        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-01T10:00:00",
                        null,
                        null,
                        5
                ),
                ConversationActivitySnapshot.unavailable(),
                fiveStepConfiguration()
        );

        assertEquals(
                PendingNegotiationDecision.Action.EXPIRE,
                decision.action()
        );
        assertTrue(decision.reason().contains("at least 48h"));
    }

    @Test
    public void finalUntouchedPendingOfferStillWaitsBefore48Hours() {
        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-01T10:00:01",
                        null,
                        null,
                        5
                ),
                ConversationActivitySnapshot.unavailable(),
                fiveStepConfiguration()
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(decision.reason().contains("waits up to 48h"));
    }

    @Test
    public void readFinalStepUsesRejectionWaitInsteadOfUntouched48HourTimer() {
        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-02T00:00:00",
                        null,
                        "2026-01-02T09:00:00",
                        5
                ),
                new ConversationActivitySnapshot(
                        true,
                        true,
                        false,
                        null,
                        null,
                        true
                ),
                fiveStepConfiguration()
        );

        assertEquals(
                PendingNegotiationDecision.Action.EXPIRE,
                decision.action()
        );
        assertTrue(
                decision.reason().contains(
                        "configured 24h rejection wait elapsed"
                )
        );
    }

    @Test
    public void staleReadFromPreviousStepDoesNotTriggerCurrentStepReactionPolicy() {
        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-03T08:00:00",
                        null,
                        "2026-01-03T07:00:00",
                        2
                ),
                ConversationActivitySnapshot.unavailable(),
                fiveStepConfiguration()
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(decision.reason().contains("no seller message or read signal"));
    }

    private BotConfigurationDto fiveStepConfiguration() {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setAutoRaiseOfferToVintedMinimum(false);
        configuration.setNegotiationSteps(
                List.of(
                        step(1, "900", 6),
                        step(2, "1000", 6),
                        step(3, "1100", 8),
                        step(4, "1200", 16),
                        step(5, "1300", 24)
                )
        );
        return configuration;
    }

    private NegotiationStepDto step(
            int number,
            String price,
            int rejectionWaitHours
    ) {
        NegotiationStepDto step = new NegotiationStepDto();
        step.setStepNumber(number);
        step.setOfferPrice(new BigDecimal(price));
        step.setMaxAcceptedCounterOffer(
                new BigDecimal(price).add(new BigDecimal("50"))
        );
        step.setRejectionAction(
                NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
        );
        step.setRejectionWaitHours(rejectionWaitHours);
        return step;
    }

    private ListingResponseDto listing(
            String currentStepStartedAt,
            String sellerActivityAt,
            String readDetectedAt,
            int currentStep
    ) {
        return new ListingResponseDto(
                10L,
                "12345",
                "Samsung",
                "https://www.vinted.pl/items/12345",
                new BigDecimal("1500"),
                new BigDecimal("1190"),
                currentStep,
                true,
                "conversation-10",
                "https://www.vinted.pl/inbox/conversation-10",
                "NEGOTIATING",
                null,
                currentStepStartedAt,
                sellerActivityAt,
                readDetectedAt
        );
    }
}

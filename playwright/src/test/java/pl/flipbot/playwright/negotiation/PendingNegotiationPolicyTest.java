package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationReactionAction;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import pl.flipbot.playwright.model.NegotiationStepDto;

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
    public void nonFinalPendingOfferAdvancesAfterDoubleRejectionWait() {
        BotConfigurationDto configuration = fiveStepConfiguration();

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
        assertEquals(
                3,
                decision.nextStep().getStepNumber().intValue()
        );
        assertTrue(decision.reason().contains("6h x 2 = 12h"));
    }

    @Test
    public void nonFinalPendingOfferWaitsBeforeDoubleRejectionWait() {
        BotConfigurationDto configuration = fiveStepConfiguration();

        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-03T00:00:01",
                        null,
                        null,
                        2
                ),
                ConversationActivitySnapshot.unavailable(),
                configuration
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(decision.reason().contains("12h"));
    }

    @Test
    public void finalPendingOfferExpiresAfter48Hours() {
        BotConfigurationDto configuration = fiveStepConfiguration();

        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-01T10:00:00",
                        null,
                        null,
                        5
                ),
                ConversationActivitySnapshot.unavailable(),
                configuration
        );

        assertEquals(PendingNegotiationDecision.Action.EXPIRE, decision.action());
        assertTrue(decision.reason().contains("at least 48h"));
    }

    @Test
    public void finalPendingOfferStillWaitsBefore48Hours() {
        BotConfigurationDto configuration = fiveStepConfiguration();

        PendingNegotiationDecision decision = policy.decide(
                listing(
                        "2026-01-01T10:00:01",
                        null,
                        null,
                        5
                ),
                ConversationActivitySnapshot.unavailable(),
                configuration
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(decision.reason().contains("waits up to 48h"));
    }

    @Test
    public void readPendingOfferDoesNotRaisePriceAfterThreeHours() {
        PendingNegotiationDecision decision = policy.decide(
                listing("2026-01-03T07:00:00", null, "2026-01-03T08:00:00"),
                new ConversationActivitySnapshot(
                        true, true, false, null, null, true
                ),
                fiveStepConfiguration()
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(decision.reason().contains("not a formal response"));
    }

    @Test
    public void sellerChatMessageDoesNotRaisePriceWhileOfferIsStillPending() {
        PendingNegotiationDecision decision = policy.decide(
                listing("2026-01-03T07:00:00", "2026-01-03T08:00:00", null),
                new ConversationActivitySnapshot(
                        true,
                        true,
                        true,
                        "Czy cena aktualna?",
                        LocalDateTime.parse("2026-01-01T11:00:00"),
                        false
                ),
                fiveStepConfiguration()
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(decision.reason().contains("no formal rejection"));
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
            String readDetectedAt
    ) {
        return listing(
                currentStepStartedAt,
                sellerActivityAt,
                readDetectedAt,
                2
        );
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
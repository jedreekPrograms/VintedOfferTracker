package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PendingNegotiationPolicyTest {

    private final PendingNegotiationPolicy policy = new PendingNegotiationPolicy();

    @Test
    public void oldPendingOfferNeverExpiresFromElapsedTimeAlone() {
        PendingNegotiationDecision decision = policy.decide(
                listing("2026-01-01T10:00:00", null, null),
                ConversationActivitySnapshot.unavailable(),
                new BotConfigurationDto()
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(decision.reason().contains("remains active"));
    }

    @Test
    public void readPendingOfferDoesNotRaisePriceAfterThreeHours() {
        PendingNegotiationDecision decision = policy.decide(
                listing("2026-01-01T10:00:00", null, "2026-01-01T11:00:00"),
                new ConversationActivitySnapshot(
                        true, true, false, null, null, true
                ),
                new BotConfigurationDto()
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(decision.reason().contains("not a formal response"));
    }

    @Test
    public void sellerChatMessageDoesNotRaisePriceWhileOfferIsStillPending() {
        PendingNegotiationDecision decision = policy.decide(
                listing("2026-01-01T10:00:00", "2026-01-01T11:00:00", null),
                new ConversationActivitySnapshot(
                        true,
                        true,
                        true,
                        "Czy cena aktualna?",
                        LocalDateTime.parse("2026-01-01T11:00:00"),
                        false
                ),
                new BotConfigurationDto()
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
        assertTrue(decision.reason().contains("no formal rejection"));
    }

    private ListingResponseDto listing(
            String currentStepStartedAt,
            String sellerActivityAt,
            String readDetectedAt
    ) {
        return new ListingResponseDto(
                10L,
                "12345",
                "Samsung",
                "https://www.vinted.pl/items/12345",
                new BigDecimal("1500"),
                new BigDecimal("1190"),
                2,
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

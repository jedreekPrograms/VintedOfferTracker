package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PendingNegotiationPolicyConfigTest {

    private final PendingNegotiationPolicy policy = new PendingNegotiationPolicy();

    @Test
    public void unreadDelayAdvancesToNextStepInsteadOfExpiring() {
        BotConfigurationDto configuration = configuration(1, 1);
        ListingResponseDto listing = listing(
                LocalDateTime.now().minusHours(2),
                null
        );

        PendingNegotiationDecision decision = policy.decide(
                listing,
                new ConversationActivitySnapshot(true, true, false, null, null, false),
                configuration
        );

        assertEquals(PendingNegotiationDecision.Action.SEND_NEXT_STEP, decision.action());
        assertEquals(Integer.valueOf(2), decision.nextStep().getStepNumber());
    }

    @Test
    public void readDelayUsesCurrentStepConfiguration() {
        BotConfigurationDto configuration = configuration(1, 24);
        ListingResponseDto listing = listing(
                LocalDateTime.now().minusHours(5),
                LocalDateTime.now().minusHours(2)
        );

        PendingNegotiationDecision decision = policy.decide(
                listing,
                new ConversationActivitySnapshot(true, true, false, null, null, true),
                configuration
        );

        assertEquals(PendingNegotiationDecision.Action.SEND_NEXT_STEP, decision.action());
        assertEquals(Integer.valueOf(2), decision.nextStep().getStepNumber());
    }

    @Test
    public void unreadDelayStillWaitsBeforeConfiguredDeadline() {
        BotConfigurationDto configuration = configuration(1, 24);
        ListingResponseDto listing = listing(
                LocalDateTime.now().minusHours(2),
                null
        );

        PendingNegotiationDecision decision = policy.decide(
                listing,
                new ConversationActivitySnapshot(true, true, false, null, null, false),
                configuration
        );

        assertEquals(PendingNegotiationDecision.Action.WAIT, decision.action());
    }

    private BotConfigurationDto configuration(int readWaitHours, int unreadWaitHours) {
        NegotiationStepDto first = new NegotiationStepDto();
        first.setStepNumber(1);
        first.setOfferPrice(new BigDecimal("1000"));
        first.setReadWaitHours(readWaitHours);
        first.setUnreadWaitHours(unreadWaitHours);

        NegotiationStepDto second = new NegotiationStepDto();
        second.setStepNumber(2);
        second.setOfferPrice(new BigDecimal("1100"));
        second.setReadWaitHours(3);
        second.setUnreadWaitHours(48);

        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setAutoRaiseOfferToVintedMinimum(false);
        configuration.setNegotiationSteps(List.of(first, second));
        return configuration;
    }

    private ListingResponseDto listing(
            LocalDateTime stepStartedAt,
            LocalDateTime readDetectedAt
    ) {
        return new ListingResponseDto(
                1L,
                "market-1",
                "Samsung Galaxy S25",
                "https://www.vinted.pl/items/market-1",
                new BigDecimal("1500"),
                new BigDecimal("1000"),
                1,
                true,
                "conversation-1",
                "https://www.vinted.pl/inbox/conversation-1",
                "NEGOTIATING",
                null,
                stepStartedAt.toString(),
                null,
                readDetectedAt == null ? null : readDetectedAt.toString(),
                null,
                null
        );
    }
}

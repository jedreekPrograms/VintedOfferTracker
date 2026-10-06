package pl.flipbot.listing;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.listing.dto.ReopenNegotiationRequest;
import pl.flipbot.mapper.ListingMapper;
import pl.flipbot.negotiation.strategy.NegotiationStrategySnapshotService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListingServiceNegotiationRecoveryTest {

    @Test
    void reopeningTechnicalTerminalConversationPreservesStepTimers() {
        ListingRepository listingRepository = mock(ListingRepository.class);
        ListingMapper listingMapper = mock(ListingMapper.class);
        NegotiationRecoveryCandidateService recovery =
                mock(NegotiationRecoveryCandidateService.class);
        NegotiationStrategySnapshotService snapshots =
                mock(NegotiationStrategySnapshotService.class);

        ListingService service = new ListingService(
                listingRepository,
                mock(BotRepository.class),
                listingMapper,
                mock(ListingClaimService.class),
                mock(ListingRediscoveryService.class),
                snapshots,
                recovery
        );

        LocalDateTime stepStarted =
                LocalDateTime.of(2026, 10, 4, 18, 0);
        LocalDateTime responseDetected =
                LocalDateTime.of(2026, 10, 4, 19, 0);
        LocalDateTime decisionAt =
                LocalDateTime.of(2026, 10, 5, 1, 0);

        Listing listing = Listing.builder()
                .id(73L)
                .listingId("10200000001")
                .title("Samsung Galaxy S25")
                .url("https://www.vinted.pl/items/10200000001")
                .originalPrice(new BigDecimal("1700.00"))
                .currentPrice(new BigDecimal("1230.00"))
                .currentStep(3)
                .awaitingSellerResponse(false)
                .conversationId("conversation-73")
                .conversationUrl("https://www.vinted.pl/inbox/conversation-73")
                .status(ListingStatus.REJECTED)
                .currentStepStartedAt(stepStarted)
                .formalResponseFingerprint("REJECTED:3")
                .formalResponseDetectedAt(responseDetected)
                .decisionAt(decisionAt)
                .build();

        when(listingRepository.findByIdAndBotId(73L, 3L))
                .thenReturn(Optional.of(listing));
        when(recovery.isWatchedTerminalStatus(ListingStatus.REJECTED))
                .thenReturn(true);
        when(listingMapper.map(listing)).thenReturn(null);

        service.reopenNegotiationForRecovery(
                3L,
                73L,
                new ReopenNegotiationRequest(
                        false,
                        "live conversation still has another step"
                )
        );

        assertEquals(ListingStatus.NEGOTIATING, listing.getStatus());
        assertEquals(false, listing.getAwaitingSellerResponse());
        assertNull(listing.getDecisionAt());
        assertNull(listing.getLastTerminalWatchAt());

        assertEquals(3, listing.getCurrentStep());
        assertEquals(new BigDecimal("1230.00"), listing.getCurrentPrice());
        assertSame(stepStarted, listing.getCurrentStepStartedAt());
        assertEquals("REJECTED:3", listing.getFormalResponseFingerprint());
        assertSame(responseDetected, listing.getFormalResponseDetectedAt());
    }
}

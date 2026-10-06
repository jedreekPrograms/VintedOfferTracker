package pl.flipbot.listing;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.listing.dto.NegotiationActivityRequest;
import pl.flipbot.mapper.ListingMapper;
import pl.flipbot.negotiation.strategy.NegotiationStrategySnapshotService;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListingServiceNegotiationActivityTest {

    @Test
    void laterSellerMessagesDoNotRestartCurrentStepReactionTimer() {
        ListingRepository listingRepository = mock(ListingRepository.class);

        Listing listing = Listing.builder()
                .id(73L)
                .listingId("marketplace-73")
                .status(ListingStatus.NEGOTIATING)
                .currentStep(2)
                .currentStepStartedAt(
                        LocalDateTime.of(2026, 1, 3, 7, 0)
                )
                .build();

        when(listingRepository.findByIdAndBotId(73L, 3L))
                .thenReturn(Optional.of(listing));

        ListingService service = new ListingService(
                listingRepository,
                mock(BotRepository.class),
                mock(ListingMapper.class),
                mock(ListingClaimService.class),
                mock(ListingRediscoveryService.class),
                mock(NegotiationStrategySnapshotService.class),
                mock(NegotiationRecoveryCandidateService.class)
        );

        LocalDateTime firstMessage =
                LocalDateTime.of(2026, 1, 3, 8, 0);
        LocalDateTime laterMessage =
                LocalDateTime.of(2026, 1, 3, 9, 30);

        service.recordNegotiationActivity(
                3L,
                73L,
                new NegotiationActivityRequest(
                        firstMessage,
                        false,
                        null
                )
        );

        service.recordNegotiationActivity(
                3L,
                73L,
                new NegotiationActivityRequest(
                        laterMessage,
                        false,
                        null
                )
        );

        assertEquals(
                firstMessage,
                listing.getSellerActivityAt()
        );
    }

    @Test
    void newNegotiationStepMayRecordFreshSellerActivityAgain() {
        ListingRepository listingRepository = mock(ListingRepository.class);

        Listing listing = Listing.builder()
                .id(74L)
                .listingId("marketplace-74")
                .status(ListingStatus.NEGOTIATING)
                .currentStep(3)
                .currentStepStartedAt(
                        LocalDateTime.of(2026, 1, 3, 12, 0)
                )
                .sellerActivityAt(null)
                .build();

        when(listingRepository.findByIdAndBotId(74L, 3L))
                .thenReturn(Optional.of(listing));

        ListingService service = new ListingService(
                listingRepository,
                mock(BotRepository.class),
                mock(ListingMapper.class),
                mock(ListingClaimService.class),
                mock(ListingRediscoveryService.class),
                mock(NegotiationStrategySnapshotService.class),
                mock(NegotiationRecoveryCandidateService.class)
        );

        LocalDateTime currentStepMessage =
                LocalDateTime.of(2026, 1, 3, 12, 15);

        service.recordNegotiationActivity(
                3L,
                74L,
                new NegotiationActivityRequest(
                        currentStepMessage,
                        false,
                        null
                )
        );

        assertEquals(
                currentStepMessage,
                listing.getSellerActivityAt()
        );
    }
}

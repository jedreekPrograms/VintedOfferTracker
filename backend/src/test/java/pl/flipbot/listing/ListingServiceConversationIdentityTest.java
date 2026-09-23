package pl.flipbot.listing;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.listing.dto.UpdateConversationIdentityRequest;
import pl.flipbot.mapper.ListingMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListingServiceConversationIdentityTest {

    @Test
    void canonicalConversationUpdateDoesNotTouchNegotiationStateOrTimers() {
        ListingRepository listingRepository = mock(ListingRepository.class);
        BotRepository botRepository = mock(BotRepository.class);
        ListingMapper listingMapper = mock(ListingMapper.class);
        ListingClaimService listingClaimService = mock(ListingClaimService.class);
        ListingRediscoveryService listingRediscoveryService =
                mock(ListingRediscoveryService.class);

        ListingService service = new ListingService(
                listingRepository,
                botRepository,
                listingMapper,
                listingClaimService,
                listingRediscoveryService
        );

        LocalDateTime stepStartedAt =
                LocalDateTime.of(2026, 9, 11, 14, 40, 25);
        LocalDateTime readDetectedAt =
                LocalDateTime.of(2026, 9, 12, 12, 45, 11);
        LocalDateTime formalDetectedAt =
                LocalDateTime.of(2026, 9, 12, 12, 45, 11);

        Listing listing = Listing.builder()
                .id(3391L)
                .listingId("9818607375")
                .title("Samsung Galaxy S25 FE")
                .url("https://www.vinted.pl/items/9818607375")
                .originalPrice(new BigDecimal("1499.66"))
                .currentPrice(new BigDecimal("950.00"))
                .currentStep(1)
                .awaitingSellerResponse(true)
                .conversationId("01a0907b-ac02-72d6-8504-c97d3291a5c4")
                .conversationUrl(
                        "https://www.vinted.pl/inbox/01a0907b-ac02-72d6-8504-c97d3291a5c4"
                )
                .status(ListingStatus.NEGOTIATING)
                .currentStepStartedAt(stepStartedAt)
                .readDetectedAt(readDetectedAt)
                .formalResponseFingerprint("REJECTED:1")
                .formalResponseDetectedAt(formalDetectedAt)
                .build();

        when(listingRepository.findByIdAndBotId(3391L, 13L))
                .thenReturn(Optional.of(listing));
        when(listingMapper.map(listing))
                .thenReturn(null);

        UpdateConversationIdentityRequest request =
                new UpdateConversationIdentityRequest(
                        "500035197835",
                        "https://www.vinted.pl/inbox/500035197835"
                );

        service.updateConversationIdentity(
                13L,
                3391L,
                request
        );

        assertEquals("500035197835", listing.getConversationId());
        assertEquals(
                "https://www.vinted.pl/inbox/500035197835",
                listing.getConversationUrl()
        );

        assertEquals(ListingStatus.NEGOTIATING, listing.getStatus());
        assertEquals(new BigDecimal("950.00"), listing.getCurrentPrice());
        assertEquals(1, listing.getCurrentStep());
        assertEquals(true, listing.getAwaitingSellerResponse());
        assertSame(stepStartedAt, listing.getCurrentStepStartedAt());
        assertSame(readDetectedAt, listing.getReadDetectedAt());
        assertEquals("REJECTED:1", listing.getFormalResponseFingerprint());
        assertSame(formalDetectedAt, listing.getFormalResponseDetectedAt());
    }
}

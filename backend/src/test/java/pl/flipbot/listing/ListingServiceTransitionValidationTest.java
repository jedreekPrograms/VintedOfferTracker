package pl.flipbot.listing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.listing.dto.UpdateListingRequest;
import pl.flipbot.mapper.ListingMapper;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingServiceTransitionValidationTest {

    private static final long BOT_ID = 7L;
    private static final long LISTING_ID = 11L;

    private ListingRepository listingRepository;
    private ListingService service;
    private Listing listing;

    @BeforeEach
    void setUp() {
        listingRepository = mock(ListingRepository.class);

        service = new ListingService(
                listingRepository,
                mock(BotRepository.class),
                mock(ListingMapper.class),
                mock(ListingClaimService.class)
        );

        listing = Listing.builder()
                .id(LISTING_ID)
                .listingId("marketplace-123")
                .title("Samsung Galaxy S25")
                .url("https://www.vinted.pl/items/marketplace-123")
                .originalPrice(new BigDecimal("1500.00"))
                .currentPrice(new BigDecimal("1500.00"))
                .currentStep(0)
                .awaitingSellerResponse(false)
                .status(ListingStatus.DISCOVERED)
                .build();

        when(listingRepository.findByIdAndBotIdForUpdate(LISTING_ID, BOT_ID))
                .thenReturn(Optional.of(listing));
    }

    @Test
    void firstRealNegotiationCanStartOnlyAtStepOneUnderRowLock() {
        UpdateListingRequest request = request(
                ListingStatus.NEGOTIATING,
                1,
                true
        );
        request.setConversationId("conversation-1");
        request.setConversationUrl("https://www.vinted.pl/inbox/conversation-1");

        assertDoesNotThrow(() -> service.updateListing(BOT_ID, LISTING_ID, request));

        verify(listingRepository).findByIdAndBotIdForUpdate(LISTING_ID, BOT_ID);
    }

    @Test
    void existingNegotiationCanAdvanceByExactlyOneStep() {
        listing.setStatus(ListingStatus.NEGOTIATING);
        listing.setCurrentStep(1);
        listing.setConversationId("conversation-1");
        listing.setConversationUrl("https://www.vinted.pl/inbox/conversation-1");

        UpdateListingRequest request = request(
                ListingStatus.NEGOTIATING,
                2,
                true
        );
        request.setConversationId(listing.getConversationId());
        request.setConversationUrl(listing.getConversationUrl());

        assertDoesNotThrow(() -> service.updateListing(BOT_ID, LISTING_ID, request));
    }

    @Test
    void discoveredListingCannotJumpDirectlyToLaterNegotiationStep() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateListing(
                        BOT_ID,
                        LISTING_ID,
                        request(ListingStatus.NEGOTIATING, 2, true)
                )
        );

        assertTrue(exception.getMessage().contains("currentStep=1"));
    }

    @Test
    void existingNegotiationCannotSkipAStep() {
        listing.setStatus(ListingStatus.NEGOTIATING);
        listing.setCurrentStep(1);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateListing(
                        BOT_ID,
                        LISTING_ID,
                        request(ListingStatus.NEGOTIATING, 3, true)
                )
        );

        assertTrue(exception.getMessage().contains("advance by exactly one"));
    }

    @Test
    void existingNegotiationCannotMoveStepBackwards() {
        listing.setStatus(ListingStatus.NEGOTIATING);
        listing.setCurrentStep(2);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateListing(
                        BOT_ID,
                        LISTING_ID,
                        request(ListingStatus.NEGOTIATING, 1, true)
                )
        );

        assertTrue(exception.getMessage().contains("advance by exactly one"));
    }

    @Test
    void terminalStatusUpdateCannotRewriteNegotiationStep() {
        listing.setStatus(ListingStatus.NEGOTIATING);
        listing.setCurrentStep(2);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.updateListing(
                        BOT_ID,
                        LISTING_ID,
                        request(ListingStatus.REJECTED, 3, false)
                )
        );

        assertTrue(exception.getMessage().contains("cannot change negotiation step"));
    }

    @Test
    void terminalStatusUpdateCanPreserveCurrentStep() {
        listing.setStatus(ListingStatus.NEGOTIATING);
        listing.setCurrentStep(2);

        assertDoesNotThrow(
                () -> service.updateListing(
                        BOT_ID,
                        LISTING_ID,
                        request(ListingStatus.REJECTED, 2, false)
                )
        );
    }

    private UpdateListingRequest request(
            ListingStatus status,
            int currentStep,
            boolean awaitingSellerResponse
    ) {
        UpdateListingRequest request = new UpdateListingRequest();
        request.setStatus(status);
        request.setCurrentPrice(new BigDecimal("1400.00"));
        request.setCurrentStep(currentStep);
        request.setAwaitingSellerResponse(awaitingSellerResponse);
        return request;
    }
}

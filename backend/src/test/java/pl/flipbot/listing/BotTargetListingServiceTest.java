package pl.flipbot.listing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.listing.dto.CreateListingRequest;
import pl.flipbot.listing.dto.DiscoverListingsRequest;
import pl.flipbot.listing.dto.ListingResponse;
import pl.flipbot.mapper.ListingMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotTargetListingServiceTest {

    private static final long BOT_ID = 4L;

    private ListingRepository listingRepository;
    private ListingClaimService listingClaimService;
    private ListingRediscoveryService listingRediscoveryService;
    private ListingMapper listingMapper;
    private BotRepository botRepository;
    private BotAdditionalTargetRepository additionalTargetRepository;
    private BotTargetListingService service;

    @BeforeEach
    void setUp() {
        listingRepository = mock(ListingRepository.class);
        listingClaimService = mock(ListingClaimService.class);
        listingRediscoveryService = mock(ListingRediscoveryService.class);
        listingMapper = mock(ListingMapper.class);
        botRepository = mock(BotRepository.class);
        additionalTargetRepository = mock(BotAdditionalTargetRepository.class);

        service = new BotTargetListingService(
                listingRepository,
                listingClaimService,
                listingRediscoveryService,
                listingMapper,
                botRepository,
                additionalTargetRepository
        );

        when(botRepository.existsById(BOT_ID)).thenReturn(true);
    }

    @Test
    void newAdditionalProductReclaimsSafeListingFromDisabledPreviousTarget() {
        BotAdditionalTarget oldTarget = BotAdditionalTarget.builder()
                .id(10L)
                .active(false)
                .build();
        BotAdditionalTarget newTarget = BotAdditionalTarget.builder()
                .id(20L)
                .active(true)
                .build();

        Listing existing = listing(oldTarget);
        CreateListingRequest requestListing = requestListing();
        DiscoverListingsRequest request = new DiscoverListingsRequest();
        request.setListings(List.of(requestListing));

        ListingResponse mapped = ListingResponse.builder()
                .id(existing.getId())
                .listingId(existing.getListingId())
                .additionalTargetId(20L)
                .status(ListingStatus.DISCOVERED.name())
                .build();

        when(additionalTargetRepository.findByIdAndConfigurationBotId(20L, BOT_ID))
                .thenReturn(Optional.of(newTarget));
        when(listingRepository.findAllByBotIdAndListingIdIn(
                eq(BOT_ID),
                any()
        )).thenReturn(List.of(existing));
        when(listingRediscoveryService
                .isSafeForInactiveTargetReassignment(existing))
                .thenReturn(true);
        when(listingRediscoveryService.reassignFromInactiveTargetIfEligible(
                BOT_ID,
                existing.getListingId(),
                newTarget,
                requestListing
        )).thenReturn(Optional.of(existing));
        when(listingMapper.map(existing)).thenReturn(mapped);

        List<ListingResponse> result = service.discoverAdditionalTarget(
                BOT_ID,
                20L,
                request
        );

        assertEquals(1, result.size());
        assertSame(mapped, result.get(0));
        verify(listingRediscoveryService)
                .isSafeForInactiveTargetReassignment(existing);
        verify(listingRediscoveryService).reassignFromInactiveTargetIfEligible(
                BOT_ID,
                existing.getListingId(),
                newTarget,
                requestListing
        );
        verify(listingClaimService, never()).claimListing(
                eq(BOT_ID),
                any(BotAdditionalTarget.class),
                any(CreateListingRequest.class)
        );
    }

    @Test
    void protectedNegotiatingOverlapSkipsLockedReassignmentEntirely() {
        BotAdditionalTarget oldTarget = BotAdditionalTarget.builder()
                .id(10L)
                .active(false)
                .build();
        BotAdditionalTarget newTarget = BotAdditionalTarget.builder()
                .id(20L)
                .active(true)
                .build();

        Listing existing = listing(oldTarget);
        existing.setStatus(ListingStatus.NEGOTIATING);
        existing.setCurrentStep(1);
        existing.setAwaitingSellerResponse(true);

        CreateListingRequest requestListing = requestListing();
        DiscoverListingsRequest request = new DiscoverListingsRequest();
        request.setListings(List.of(requestListing));

        when(additionalTargetRepository.findByIdAndConfigurationBotId(20L, BOT_ID))
                .thenReturn(Optional.of(newTarget));
        when(listingRepository.findAllByBotIdAndListingIdIn(
                eq(BOT_ID),
                any()
        )).thenReturn(List.of(existing));
        when(listingRediscoveryService
                .isSafeForInactiveTargetReassignment(existing))
                .thenReturn(false);

        List<ListingResponse> result = service.discoverAdditionalTarget(
                BOT_ID,
                20L,
                request
        );

        assertEquals(0, result.size());
        verify(listingRediscoveryService, never())
                .reassignFromInactiveTargetIfEligible(
                        any(),
                        any(),
                        any(),
                        any()
                );
        verify(listingClaimService, never()).claimListing(
                eq(BOT_ID),
                any(BotAdditionalTarget.class),
                any(CreateListingRequest.class)
        );
    }

    @Test
    void overlapOwnedByAnotherActiveProductSkipsLockedReassignmentEntirely() {
        BotAdditionalTarget oldTarget = BotAdditionalTarget.builder()
                .id(10L)
                .active(true)
                .build();
        BotAdditionalTarget newTarget = BotAdditionalTarget.builder()
                .id(20L)
                .active(true)
                .build();

        Listing existing = listing(oldTarget);
        CreateListingRequest requestListing = requestListing();
        DiscoverListingsRequest request = new DiscoverListingsRequest();
        request.setListings(List.of(requestListing));

        when(additionalTargetRepository.findByIdAndConfigurationBotId(20L, BOT_ID))
                .thenReturn(Optional.of(newTarget));
        when(listingRepository.findAllByBotIdAndListingIdIn(
                eq(BOT_ID),
                any()
        )).thenReturn(List.of(existing));
        when(listingRediscoveryService
                .isSafeForInactiveTargetReassignment(existing))
                .thenReturn(false);

        List<ListingResponse> result = service.discoverAdditionalTarget(
                BOT_ID,
                20L,
                request
        );

        assertEquals(0, result.size());
        verify(listingRediscoveryService, never())
                .reassignFromInactiveTargetIfEligible(
                        any(),
                        any(),
                        any(),
                        any()
                );
    }

    private Listing listing(BotAdditionalTarget target) {
        return Listing.builder()
                .id(99L)
                .listingId("123")
                .title("Samsung Galaxy S25")
                .url("https://www.vinted.pl/items/123")
                .originalPrice(new BigDecimal("1900.00"))
                .currentPrice(new BigDecimal("1900.00"))
                .currentStep(0)
                .awaitingSellerResponse(false)
                .status(ListingStatus.DISCOVERED)
                .additionalTarget(target)
                .build();
    }

    private CreateListingRequest requestListing() {
        CreateListingRequest request = new CreateListingRequest();
        request.setListingId("123");
        request.setTitle("Samsung Galaxy S25 256 GB");
        request.setUrl("https://www.vinted.pl/items/123-new");
        request.setOriginalPrice(new BigDecimal("1750.00"));
        return request;
    }
}

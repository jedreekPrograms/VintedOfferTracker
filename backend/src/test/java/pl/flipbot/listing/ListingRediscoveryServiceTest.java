package pl.flipbot.listing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.listing.dto.CreateListingRequest;
import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;
import pl.flipbot.negotiation.guard.RealActionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingRediscoveryServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    private ListingRepository listingRepository;
    private RealActionAuditRepository realActionAuditRepository;
    private ListingRediscoveryService service;

    @BeforeEach
    void setUp() {
        listingRepository = mock(ListingRepository.class);
        realActionAuditRepository = mock(RealActionAuditRepository.class);
        service = new ListingRediscoveryService(
                listingRepository,
                realActionAuditRepository
        );
    }

    @Test
    void transientNeverStartedListingCanReturnOnceOnNextWarsawDay() {
        LocalDate today = LocalDate.now(WARSAW);
        Listing listing = listing(
                ListingStatus.UNAVAILABLE,
                today.minusDays(1).atTime(23, 50)
        );
        listing.setSellerActivityAt(LocalDateTime.of(2026, 8, 31, 12, 0));
        listing.setFormalResponseFingerprint("legacy-state");

        CreateListingRequest fresh = freshRequest(
                "Samsung Galaxy S25 256 GB",
                "https://www.vinted.pl/items/123-new",
                "1750.00"
        );

        when(listingRepository.findByBotIdAndListingIdForUpdate(4L, "123"))
                .thenReturn(Optional.of(listing));
        when(realActionAuditRepository.existsByBackendListingIdAndActionTypeAndOutcome(
                99L,
                RealActionType.FIRST_OFFER,
                RealActionAuditOutcome.CONFIRMED
        )).thenReturn(false);
        when(listingRepository.saveAndFlush(listing)).thenReturn(listing);

        Optional<Listing> result = service.requalifyIfEligible(
                4L,
                "123",
                fresh
        );

        assertTrue(result.isPresent());
        assertEquals(ListingStatus.DISCOVERED, listing.getStatus());
        assertEquals(0, listing.getCurrentStep());
        assertEquals(new BigDecimal("1750.00"), listing.getOriginalPrice());
        assertEquals(new BigDecimal("1750.00"), listing.getCurrentPrice());
        assertEquals("Samsung Galaxy S25 256 GB", listing.getTitle());
        assertEquals("https://www.vinted.pl/items/123-new", listing.getUrl());
        assertEquals(today, listing.getLastFreshDiscoveryAt().toLocalDate());
        assertNull(listing.getSellerActivityAt());
        assertNull(listing.getFormalResponseFingerprint());
        verify(listingRepository).saveAndFlush(listing);
    }

    @Test
    void sameWarsawDayDoesNotRecycleTransientListingAgain() {
        LocalDate today = LocalDate.now(WARSAW);
        Listing listing = listing(
                ListingStatus.SKIPPED_CANNOT_NEGOTIATE,
                today.atStartOfDay()
        );

        when(listingRepository.findByBotIdAndListingIdForUpdate(4L, "123"))
                .thenReturn(Optional.of(listing));

        Optional<Listing> result = service.requalifyIfEligible(
                4L,
                "123",
                freshRequest(
                        "Samsung Galaxy S25",
                        "https://www.vinted.pl/items/123",
                        "1800.00"
                )
        );

        assertTrue(result.isEmpty());
        verify(realActionAuditRepository, never())
                .existsByBackendListingIdAndActionTypeAndOutcome(
                        any(),
                        any(),
                        any()
                );
        verify(listingRepository, never()).saveAndFlush(any());
    }

    @Test
    void confirmedFirstOfferCanNeverBeRequalifiedByFreshDiscovery() {
        Listing listing = listing(
                ListingStatus.UNAVAILABLE,
                null
        );

        when(listingRepository.findByBotIdAndListingIdForUpdate(4L, "123"))
                .thenReturn(Optional.of(listing));
        when(realActionAuditRepository.existsByBackendListingIdAndActionTypeAndOutcome(
                99L,
                RealActionType.FIRST_OFFER,
                RealActionAuditOutcome.CONFIRMED
        )).thenReturn(true);

        Optional<Listing> result = service.requalifyIfEligible(
                4L,
                "123",
                freshRequest(
                        "Samsung Galaxy S25",
                        "https://www.vinted.pl/items/123",
                        "1800.00"
                )
        );

        assertTrue(result.isEmpty());
        assertEquals(ListingStatus.UNAVAILABLE, listing.getStatus());
        verify(listingRepository, never()).saveAndFlush(any());
    }

    @Test
    void conversationOrStartedStepIsNeverARefreshCandidate() {
        LocalDate today = LocalDate.of(2026, 9, 2);

        Listing withConversation = listing(
                ListingStatus.UNAVAILABLE,
                null
        );
        withConversation.setConversationId("24760000000");

        Listing withStartedStep = listing(
                ListingStatus.SKIPPED_TARGET_MISMATCH,
                null
        );
        withStartedStep.setCurrentStep(1);

        assertFalse(service.shouldAttemptRequalification(withConversation, today));
        assertFalse(service.shouldAttemptRequalification(withStartedStep, today));
    }

    @Test
    void durableTerminalStatesAreNeverDailyRefreshCandidates() {
        LocalDate today = LocalDate.of(2026, 9, 2);

        assertFalse(service.shouldAttemptRequalification(
                listing(ListingStatus.PURCHASED, null),
                today
        ));
        assertFalse(service.shouldAttemptRequalification(
                listing(ListingStatus.SKIPPED_BY_USER, null),
                today
        ));
        assertFalse(service.shouldAttemptRequalification(
                listing(ListingStatus.SKIPPED_ALREADY_NEGOTIATED, null),
                today
        ));
        assertFalse(service.shouldAttemptRequalification(
                listing(ListingStatus.FINISHED, null),
                today
        ));
        assertFalse(service.shouldAttemptRequalification(
                listing(ListingStatus.EXPIRED, null),
                today
        ));
    }

    private Listing listing(
            ListingStatus status,
            LocalDateTime lastFreshDiscoveryAt
    ) {
        return Listing.builder()
                .id(99L)
                .listingId("123")
                .title("Samsung Galaxy S25")
                .url("https://www.vinted.pl/items/123")
                .originalPrice(new BigDecimal("1900.00"))
                .currentPrice(new BigDecimal("1900.00"))
                .currentStep(0)
                .awaitingSellerResponse(false)
                .status(status)
                .lastFreshDiscoveryAt(lastFreshDiscoveryAt)
                .build();
    }

    private CreateListingRequest freshRequest(
            String title,
            String url,
            String price
    ) {
        CreateListingRequest request = new CreateListingRequest();
        request.setListingId("123");
        request.setTitle(title);
        request.setUrl(url);
        request.setOriginalPrice(new BigDecimal(price));
        return request;
    }
}

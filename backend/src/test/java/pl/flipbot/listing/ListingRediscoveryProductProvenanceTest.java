package pl.flipbot.listing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.listing.dto.CreateListingRequest;
import pl.flipbot.negotiation.audit.RealActionAuditOutcome;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;
import pl.flipbot.negotiation.guard.RealActionType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListingRediscoveryProductProvenanceTest {

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
    void dailyRediscoveryPreservesOriginalProductSnapshot() {
        Listing listing = Listing.builder()
                .id(99L)
                .listingId("123")
                .title("Old title")
                .url("https://www.vinted.pl/items/123")
                .originalPrice(new BigDecimal("1900.00"))
                .currentPrice(new BigDecimal("1900.00"))
                .currentStep(0)
                .awaitingSellerResponse(false)
                .status(ListingStatus.UNAVAILABLE)
                .lastFreshDiscoveryAt(LocalDateTime.now().minusDays(1))
                .productTargetLabel("Samsung → Galaxy S24")
                .build();

        prepareListing(listing);

        Optional<Listing> result = service.requalifyIfEligible(
                4L,
                "123",
                freshRequest()
        );

        assertTrue(result.isPresent());
        assertEquals("Samsung → Galaxy S24", listing.getProductTargetLabel());
    }

    @Test
    void reassignmentSnapshotsReplacementProduct() {
        BotAdditionalTarget oldTarget = BotAdditionalTarget.builder()
                .id(10L)
                .active(false)
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("Galaxy S24")
                .build();
        BotAdditionalTarget replacementTarget = BotAdditionalTarget.builder()
                .id(20L)
                .active(true)
                .brand("Apple")
                .targetMode(TargetMode.SEARCH_QUERY)
                .searchQuery("iPhone 16 Pro")
                .build();

        Listing listing = Listing.builder()
                .id(99L)
                .listingId("123")
                .title("Old title")
                .url("https://www.vinted.pl/items/123")
                .originalPrice(new BigDecimal("1900.00"))
                .currentPrice(new BigDecimal("1900.00"))
                .currentStep(0)
                .awaitingSellerResponse(false)
                .status(ListingStatus.DISCOVERED)
                .productTargetLabel("Samsung → Galaxy S24")
                .additionalTarget(oldTarget)
                .build();

        prepareListing(listing);

        Optional<Listing> result = service.reassignFromInactiveTargetIfEligible(
                4L,
                "123",
                replacementTarget,
                freshRequest()
        );

        assertTrue(result.isPresent());
        assertSame(replacementTarget, listing.getAdditionalTarget());
        assertEquals("Apple → iPhone 16 Pro", listing.getProductTargetLabel());
    }

    private void prepareListing(Listing listing) {
        when(listingRepository.findByBotIdAndListingIdForUpdate(4L, "123"))
                .thenReturn(Optional.of(listing));
        when(realActionAuditRepository.existsByBackendListingIdAndActionTypeAndOutcome(
                99L,
                RealActionType.FIRST_OFFER,
                RealActionAuditOutcome.CONFIRMED
        )).thenReturn(false);
        when(realActionAuditRepository.existsByBackendListingIdAndActionTypeAndOutcome(
                99L,
                RealActionType.FIRST_OFFER,
                RealActionAuditOutcome.AMBIGUOUS
        )).thenReturn(false);
        when(listingRepository.saveAndFlush(listing)).thenReturn(listing);
    }

    private CreateListingRequest freshRequest() {
        CreateListingRequest request = new CreateListingRequest();
        request.setListingId("123");
        request.setTitle("Fresh title");
        request.setUrl("https://www.vinted.pl/items/123-new");
        request.setOriginalPrice(new BigDecimal("1750.00"));
        return request;
    }
}

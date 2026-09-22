package pl.flipbot.negotiation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NegotiationServiceBuyCandidateHistoryTest {

    private ListingRepository listingRepository;
    private NegotiationEngine negotiationEngine;
    private NegotiationService service;

    @BeforeEach
    void setUp() {
        listingRepository = mock(ListingRepository.class);
        negotiationEngine = mock(NegotiationEngine.class);
        service = new NegotiationService(
                listingRepository,
                negotiationEngine
        );
    }

    @Test
    void actionRequiredDecisionMarksListingAsBuyCandidate() {
        Listing listing = listing();
        NegotiationDecision decision = NegotiationDecision.builder()
                .action(NegotiationAction.ACTION_REQUIRED)
                .build();

        when(listingRepository.findByBotIdAndListingId(3L, "market-1"))
                .thenReturn(Optional.of(listing));
        when(negotiationEngine.processNegotiationResult(
                listing,
                NegotiationResult.ACCEPTED,
                null
        )).thenReturn(decision);

        service.processNegotiationResult(
                3L,
                "market-1",
                NegotiationResult.ACCEPTED,
                null
        );

        assertEquals(ListingStatus.ACTION_REQUIRED, listing.getStatus());
        assertNotNull(listing.getBuyCandidateAt());
    }

    @Test
    void ordinaryNextStepDoesNotMarkListingAsBuyCandidate() {
        Listing listing = listing();
        NegotiationDecision decision = NegotiationDecision.builder()
                .action(NegotiationAction.SEND_NEXT_OFFER)
                .nextStep(2)
                .offerPrice(new BigDecimal("950.00"))
                .build();

        when(listingRepository.findByBotIdAndListingId(3L, "market-1"))
                .thenReturn(Optional.of(listing));
        when(negotiationEngine.processNegotiationResult(
                listing,
                NegotiationResult.REJECTED,
                null
        )).thenReturn(decision);

        service.processNegotiationResult(
                3L,
                "market-1",
                NegotiationResult.REJECTED,
                null
        );

        assertEquals(ListingStatus.NEGOTIATING, listing.getStatus());
        assertNull(listing.getBuyCandidateAt());
    }

    private Listing listing() {
        return Listing.builder()
                .listingId("market-1")
                .title("Samsung Galaxy S26")
                .url("https://www.vinted.pl/items/market-1")
                .originalPrice(new BigDecimal("1200.00"))
                .currentPrice(new BigDecimal("1000.00"))
                .currentStep(1)
                .awaitingSellerResponse(true)
                .status(ListingStatus.NEGOTIATING)
                .build();
    }
}

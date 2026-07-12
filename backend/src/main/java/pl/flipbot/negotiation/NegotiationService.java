package pl.flipbot.negotiation;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class NegotiationService {

    private final ListingRepository listingRepository;
    private final NegotiationEngine negotiationEngine;

    @Transactional
    public NegotiationDecision processNegotiationResult(
            String listingId,
            NegotiationResult result,
            BigDecimal counterOffer
    ) {



        Listing listing = listingRepository.findByListingId(listingId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Listing not found")
                );

        if (!listing.getAwaitingSellerResponse()) {
            throw new IllegalStateException(
                    "Listing is not waiting for seller response."
            );
        }

        NegotiationDecision decision =
                negotiationEngine.processNegotiationResult(
                        listing,
                        result,
                        counterOffer
                );

        updateListing(listing, decision, counterOffer);

        return decision;
    }

    @Transactional
    public void markOfferAsSent(String listingId) {

        Listing listing = listingRepository.findByListingId(listingId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Listing not found")
                );

        listing.setAwaitingSellerResponse(true);
    }

    private void updateListing(
            Listing listing,
            NegotiationDecision decision,
            BigDecimal counterOffer
    ) {

        switch (decision.getAction()) {

            case ACTION_REQUIRED -> {

                listing.setAwaitingSellerResponse(false);

                listing.setStatus(ListingStatus.ACTION_REQUIRED);

                if (counterOffer != null) {
                    listing.setCurrentPrice(counterOffer);
                }
            }

            case SEND_NEXT_OFFER -> {

                listing.setCurrentStep(decision.getNextStep());

                listing.setCurrentPrice(
                        decision.getOfferPrice()
                );

                listing.setAwaitingSellerResponse(false);

                listing.setStatus(ListingStatus.NEGOTIATING);
            }

            case FINISH_NEGOTIATION -> {

                listing.setAwaitingSellerResponse(false);

                listing.setStatus(ListingStatus.FINISHED);
            }
        }
    }
}

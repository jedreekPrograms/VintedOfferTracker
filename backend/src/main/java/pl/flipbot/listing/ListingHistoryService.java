package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.listing.dto.ListingHistoryResponse;
import pl.flipbot.listing.dto.UpdateHistoryClassificationRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ListingHistoryService {

    private final ListingRepository listingRepository;

    @Transactional(readOnly = true)
    public List<ListingHistoryResponse> getHistory() {
        return listingRepository
                .findAll()
                .stream()
                .filter(this::isVisibleHistoryListing)
                .sorted(
                        Comparator.comparing(
                                Listing::getDecisionAt,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                )
                .map(this::map)
                .toList();
    }

    @Transactional
    public ListingHistoryResponse updateClassification(
            Long listingId,
            UpdateHistoryClassificationRequest request
    ) {
        if (request == null
                || request.historyOutcome() == null
                || request.offerAssessment() == null) {
            throw new IllegalArgumentException(
                    "History outcome and offer assessment are required."
            );
        }

        Listing listing = getVisibleHistoryListing(listingId);

        /*
         * NULL is reserved for untouched legacy rows where effectiveOutcome()
         * provides the backward-compatible mapping. Once the operator makes an
         * explicit choice, including "UNCLASSIFIED", persist it verbatim so the
         * choice is not silently replaced by the old technical status.
         */
        listing.setHistoryOutcome(request.historyOutcome());
        listing.setOfferAssessment(request.offerAssessment());

        if (request.historyOutcome() == HistoryOutcome.MISSED_OPPORTUNITY) {
            listing.setMissedOpportunityReason(
                    request.missedOpportunityReason()
            );
        } else {
            listing.setMissedOpportunityReason(null);
        }

        listingRepository.save(listing);
        return map(listing);
    }

    @Transactional
    public ListingHistoryResponse updatePurchasePrice(
            Long listingId,
            BigDecimal purchasePrice
    ) {
        Listing listing = getVisibleHistoryListing(listingId);

        if (ListingHistoryMetadata.effectiveOutcome(listing)
                != HistoryOutcome.PURCHASED) {
            throw new IllegalStateException(
                    "Purchase price can only be edited for history entries classified as PURCHASED."
            );
        }

        if (purchasePrice == null || purchasePrice.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Purchase price must be greater than zero."
            );
        }

        BigDecimal normalizedPrice = purchasePrice.setScale(
                2,
                RoundingMode.HALF_UP
        );

        listing.setCurrentPrice(normalizedPrice);
        listingRepository.save(listing);

        return map(listing);
    }

    @Transactional
    public void hideHistoryEntry(Long listingId) {
        Listing listing = getVisibleHistoryListing(listingId);
        listing.setHistoryHidden(true);
        listingRepository.save(listing);
    }

    private Listing getVisibleHistoryListing(Long listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new NoSuchElementException(
                        "History listing " + listingId + " does not exist."
                ));

        if (!isHistoryListing(listing) || listing.isHistoryHidden()) {
            throw new NoSuchElementException(
                    "History listing " + listingId + " does not exist."
            );
        }

        return listing;
    }

    private boolean isVisibleHistoryListing(Listing listing) {
        return isHistoryListing(listing) && !listing.isHistoryHidden();
    }

    private boolean isHistoryListing(Listing listing) {
        return ListingHistoryMetadata.isHistoryListing(listing);
    }

    private ListingHistoryResponse map(Listing listing) {
        return ListingHistoryResponse
                .builder()
                .id(listing.getId())
                .listingId(listing.getListingId())
                .title(listing.getTitle())
                .url(listing.getUrl())
                .originalPrice(listing.getOriginalPrice())
                .currentPrice(listing.getCurrentPrice())
                .currentStep(listing.getCurrentStep())
                .status(listing.getStatus().name())
                .historyOutcome(
                        ListingHistoryMetadata.effectiveOutcome(listing).name()
                )
                .offerAssessment(
                        ListingHistoryMetadata.effectiveAssessment(listing).name()
                )
                .missedOpportunityReason(
                        listing.getMissedOpportunityReason() == null
                                ? null
                                : listing.getMissedOpportunityReason().name()
                )
                .decisionAt(listing.getDecisionAt())
                .botId(listing.getBot().getId())
                .botName(listing.getBot().getName())
                .additionalTargetId(
                        listing.getAdditionalTarget() == null
                                ? null
                                : listing.getAdditionalTarget().getId()
                )
                .productTargetLabel(listing.getProductTargetLabel())
                .build();
    }
}
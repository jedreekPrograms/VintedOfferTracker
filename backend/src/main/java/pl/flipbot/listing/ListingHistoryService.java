package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.listing.dto.ListingHistoryResponse;

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
                .filter(
                        this::isVisibleHistoryListing
                )
                .sorted(
                        Comparator.comparing(
                                Listing::getDecisionAt,
                                Comparator.nullsLast(
                                        Comparator.reverseOrder()
                                )
                        )
                )
                .map(
                        this::map
                )
                .toList();
    }

    @Transactional
    public ListingHistoryResponse updatePurchasePrice(
            Long listingId,
            BigDecimal purchasePrice
    ) {

        Listing listing = getVisibleHistoryListing(listingId);

        boolean purchasedForHistory = listing.getStatus() == ListingStatus.PURCHASED
                || listing.getHistoryOutcome() == ListingHistoryOutcome.PURCHASED_BY_ME;

        if (!purchasedForHistory) {
            throw new IllegalStateException(
                    "Purchase price can only be edited for history entries marked as purchased."
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
    public ListingHistoryResponse updateOutcome(
            Long listingId,
            ListingHistoryOutcome outcome
    ) {

        Listing listing = getVisibleHistoryListing(listingId);
        listing.setHistoryOutcome(outcome);
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

    private boolean isHistoryListing(
            Listing listing
    ) {

        return listing.getStatus()
                == ListingStatus.PURCHASED
                || listing.getStatus()
                == ListingStatus.SKIPPED_BY_USER;
    }

    private ListingHistoryResponse map(
            Listing listing
    ) {

        ListingHistoryOutcome effectiveOutcome = listing.getHistoryOutcome();
        if (effectiveOutcome == null && listing.getStatus() == ListingStatus.PURCHASED) {
            effectiveOutcome = ListingHistoryOutcome.PURCHASED_BY_ME;
        }

        return ListingHistoryResponse
                .builder()
                .id(
                        listing.getId()
                )
                .listingId(
                        listing.getListingId()
                )
                .title(
                        listing.getTitle()
                )
                .url(
                        listing.getUrl()
                )
                .originalPrice(
                        listing.getOriginalPrice()
                )
                .currentPrice(
                        listing.getCurrentPrice()
                )
                .currentStep(
                        listing.getCurrentStep()
                )
                .status(
                        listing.getStatus()
                                .name()
                )
                .outcome(
                        effectiveOutcome == null
                                ? null
                                : effectiveOutcome.name()
                )
                .decisionAt(
                        listing.getDecisionAt()
                )
                .botId(
                        listing.getBot()
                                .getId()
                )
                .botName(
                        listing.getBot()
                                .getName()
                )
                .build();
    }
}

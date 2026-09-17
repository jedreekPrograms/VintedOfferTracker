package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.listing.dto.ListingHistoryResponse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ListingHistoryService {

    private final ListingRepository listingRepository;

    @Transactional(readOnly = true)
    public List<ListingHistoryResponse> getHistory() {

        return listingRepository.findVisibleHistory(List.of(
                        ListingStatus.PURCHASED, ListingStatus.SKIPPED_BY_USER))
                .stream().map(row -> ListingHistoryResponse.builder()
                        .id(row.getId()).listingId(row.getListingId())
                        .title(row.getTitle()).url(row.getUrl())
                        .originalPrice(row.getOriginalPrice()).currentPrice(row.getCurrentPrice())
                        .currentStep(row.getCurrentStep()).status(row.getStatus().name())
                        .decisionAt(row.getDecisionAt()).botId(row.getBotId()).botName(row.getBotName())
                        .additionalTargetId(row.getAdditionalTargetId())
                        .productTargetLabel(row.getProductTargetLabel()).build())
                .toList();
    }

    @Transactional
    public ListingHistoryResponse updatePurchasePrice(
            Long listingId,
            BigDecimal purchasePrice
    ) {

        Listing listing = getVisibleHistoryListing(listingId);

        if (listing.getStatus() != ListingStatus.PURCHASED) {
            throw new IllegalStateException(
                    "Purchase price can only be edited for PURCHASED history entries."
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
                .additionalTargetId(
                        listing.getAdditionalTarget() == null
                                ? null
                                : listing.getAdditionalTarget().getId()
                )
                .productTargetLabel(
                        listing.getProductTargetLabel()
                )
                .build();
    }
}

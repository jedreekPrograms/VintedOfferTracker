package pl.flipbot.listing;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.listing.dto.ListingHistoryResponse;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

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
                        this::isHistoryListing
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
                .build();
    }
}
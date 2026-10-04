package pl.flipbot.negotiation.strategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;

import java.util.List;

@Slf4j
@Component
@Order(130)
@RequiredArgsConstructor
public class NegotiationStrategySnapshotBackfillRunner implements ApplicationRunner {

    private final ListingRepository listingRepository;
    private final NegotiationStrategySnapshotService snapshotService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Listing> active = listingRepository.findByStatusInOrderByIdAsc(
                List.of(ListingStatus.NEGOTIATING, ListingStatus.ACTION_REQUIRED)
        );

        int pinned = 0;
        for (Listing listing : active) {
            if (snapshotService.pinIfMissing(listing)) {
                pinned++;
            }
        }

        if (pinned > 0) {
            listingRepository.saveAll(active);
        }

        log.info(
                "[NEGOTIATION STRATEGY] Verified immutable strategy snapshots for {} active conversation(s); backfilled {} legacy row(s).",
                active.size(),
                pinned
        );
    }
}

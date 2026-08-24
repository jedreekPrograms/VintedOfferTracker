package pl.flipbot.negotiation.snapshot;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Runs before the Playwright process can safely rely on strategy snapshots.
 * Existing active conversations predate the feature, so their current live
 * strategy is frozen as LEGACY_RATIO exactly once.
 */
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NegotiationStrategySnapshotBackfill implements ApplicationRunner {

    private final NegotiationStrategySnapshotService snapshotService;

    @Override
    public void run(ApplicationArguments args) {
        snapshotService.backfillExistingActiveNegotiations();
    }
}

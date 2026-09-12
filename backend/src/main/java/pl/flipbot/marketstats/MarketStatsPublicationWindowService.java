package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class MarketStatsPublicationWindowService {

    private static final ZoneId MARKET_STATS_ZONE = ZoneId.of("Europe/Warsaw");

    private final MarketModelScanStateRepository scanStateRepository;

    @Transactional
    public LocalDateTime markPublicationWindowComplete(Long modelId) {
        if (modelId == null || modelId <= 0) {
            throw new IllegalArgumentException("Market model id must be positive.");
        }

        MarketModelScanState state = scanStateRepository
                .findByModelIdForUpdate(modelId)
                .orElseThrow(
                        () -> new NoSuchElementException(
                                "Market scan state was not found for model " + modelId
                        )
                );

        if (state.getBaselineCompleteAt() == null
                || !Boolean.TRUE.equals(state.getLastScanComplete())) {
            throw new IllegalStateException(
                    "Publication-window coverage can only be confirmed after a complete market scan for model "
                            + modelId
            );
        }

        LocalDateTime completedAt = LocalDateTime.now(MARKET_STATS_ZONE);
        state.setPublicationWindowCompleteAt(completedAt);
        scanStateRepository.save(state);

        return completedAt;
    }
}

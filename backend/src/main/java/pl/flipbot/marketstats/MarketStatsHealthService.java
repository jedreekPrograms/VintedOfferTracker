package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.marketstats.dto.MarketStatsHealthResponse;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MarketStatsHealthService {

    private static final long DEFAULT_REFRESH_MINUTES = 15L;
    private static final ZoneId MARKET_STATS_ZONE = ZoneId.of("Europe/Warsaw");

    private final DictionaryModelRepository modelRepository;
    private final MarketModelScanStateRepository scanStateRepository;

    @Transactional(readOnly = true)
    public MarketStatsHealthResponse getHealth() {
        int totalModels = safeInt(modelRepository.count());
        List<MarketModelScanState> states = scanStateRepository.findAll();

        int baselineReady = safeInt(
                states.stream()
                        .filter(state -> state.getBaselineCompleteAt() != null)
                        .count()
        );
        int pendingBaseline = Math.max(totalModels - baselineReady, 0);
        int incomplete = safeInt(
                states.stream()
                        .filter(state -> state.getLastScanAt() != null)
                        .filter(state -> !Boolean.TRUE.equals(
                                state.getLastScanComplete()
                        ))
                        .count()
        );

        LocalDateTime lastScanAt = states.stream()
                .map(MarketModelScanState::getLastScanAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        LocalDateTime lastSuccessfulScanAt = states.stream()
                .map(MarketModelScanState::getLastSuccessfulScanAt)
                .filter(java.util.Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        MarketStatsHealthStatus status = resolveStatus(
                totalModels,
                pendingBaseline,
                incomplete,
                lastScanAt,
                lastSuccessfulScanAt
        );

        return new MarketStatsHealthResponse(
                status,
                totalModels,
                baselineReady,
                pendingBaseline,
                incomplete,
                lastScanAt,
                lastSuccessfulScanAt
        );
    }

    private MarketStatsHealthStatus resolveStatus(
            int totalModels,
            int pendingBaseline,
            int incomplete,
            LocalDateTime lastScanAt,
            LocalDateTime lastSuccessfulScanAt
    ) {
        if (totalModels == 0) {
            return MarketStatsHealthStatus.IDLE;
        }

        if (lastScanAt == null) {
            return MarketStatsHealthStatus.WAITING;
        }

        if (incomplete > 0) {
            return MarketStatsHealthStatus.PARTIAL;
        }

        if (pendingBaseline > 0) {
            return MarketStatsHealthStatus.WARMING_UP;
        }

        if (lastSuccessfulScanAt == null
                || lastSuccessfulScanAt.isBefore(
                        LocalDateTime.now(MARKET_STATS_ZONE).minusMinutes(
                                staleAfterMinutes()
                        )
                )) {
            return MarketStatsHealthStatus.STALE;
        }

        return MarketStatsHealthStatus.OK;
    }

    private long staleAfterMinutes() {
        long refreshMinutes = readPositiveLong(
                System.getenv("FLIPBOT_MARKET_STATS_REFRESH_MINUTES"),
                DEFAULT_REFRESH_MINUTES
        );

        String legacyHours = System.getenv(
                "FLIPBOT_MARKET_STATS_INTERVAL_HOURS"
        );
        if ((System.getenv("FLIPBOT_MARKET_STATS_REFRESH_MINUTES") == null
                || System.getenv("FLIPBOT_MARKET_STATS_REFRESH_MINUTES").isBlank())
                && legacyHours != null
                && !legacyHours.isBlank()) {
            refreshMinutes = readPositiveLong(
                    legacyHours,
                    24L
            ) * 60L;
        }

        return Math.max(45L, refreshMinutes * 3L);
    }

    private long readPositiveLong(
            String raw,
            long fallback
    ) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0L ? parsed : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private int safeInt(long value) {
        return value > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) Math.max(value, 0L);
    }
}

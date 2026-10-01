package pl.flipbot.marketstats.dto;

import pl.flipbot.marketstats.MarketStatsHealthStatus;

import java.time.LocalDateTime;

public record MarketStatsHealthResponse(
        MarketStatsHealthStatus status,
        int totalModels,
        int baselineReadyModels,
        int pendingBaselineModels,
        int incompleteModels,
        LocalDateTime lastScanAt,
        LocalDateTime lastSuccessfulScanAt
) {
}

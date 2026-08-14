package pl.flipbot.marketstats.dto;

import java.time.LocalDateTime;

public record ModelPlanningResponse(
        Long modelId,
        Integer offersLast7Days,
        Integer recommendedBots,
        Integer existingBots,
        boolean statsReady,
        int trackedDays,
        LocalDateTime lastStatsUpdatedAt,
        boolean lastScanComplete
) {
}

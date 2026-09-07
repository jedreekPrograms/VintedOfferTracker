package pl.flipbot.marketstats.dto;

import java.time.LocalDateTime;

public record CalendarModelPlanningResponse(
        Long modelId,
        Integer baselineOffers,
        Integer offersToday,
        Integer offersCurrentWeek,
        Integer offersPreviousFullWeek,
        Integer negotiationsStartedToday,
        Integer negotiationsStartedCurrentWeek,
        Integer negotiationsStartedPreviousFullWeek,
        Double empiricalConversationsPerBotPreviousFullWeek,
        Integer recommendedBots,
        Integer recommendationWeeklyOffers,
        boolean recommendationEstimated,
        Integer existingBots,
        boolean todayWindowComplete,
        boolean currentWeekWindowComplete,
        boolean previousFullWeekAvailable,
        int trackedDays,
        LocalDateTime lastStatsUpdatedAt,
        boolean lastScanComplete
) {
}

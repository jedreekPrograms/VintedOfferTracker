export interface ModelPlanning {
    modelId: number;
    baselineOffers: number | null;
    offersToday: number | null;
    offersCurrentWeek: number | null;
    offersPreviousFullWeek: number | null;
    negotiationsStartedToday: number;
    negotiationsStartedCurrentWeek: number;
    negotiationsStartedPreviousFullWeek: number | null;
    unstartedQueueNow: number | null;
    unstartedQueueYesterdayEnd: number | null;
    unstartedQueuePreviousWeekStart: number | null;
    unstartedQueuePreviousWeekEnd: number | null;
    unstartedQueueMeasuredAt: string | null;
    empiricalConversationsPerBotPreviousFullWeek: number | null;
    recommendedBots: number | null;
    botBalance: number | null;
    recommendationWeeklyOffers: number | null;
    recommendationEstimated: boolean;
    existingBots: number;
    todayWindowComplete: boolean;
    currentWeekWindowComplete: boolean;
    previousFullWeekAvailable: boolean;
    trackedDays: number;
    lastStatsUpdatedAt: string | null;
    lastScanComplete: boolean;
}

export interface ModelPlanning {
    modelId: number;
    baselineOffers: number | null;
    offersToday: number | null;
    offersCurrentWeek: number | null;
    offersPreviousFullWeek: number | null;
    negotiationsStartedToday: number;
    negotiationsStartedCurrentWeek: number;
    negotiationsStartedPreviousFullWeek: number | null;
    empiricalConversationsPerBotPreviousFullWeek: number | null;
    recommendedBots: number | null;
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

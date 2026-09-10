export interface ModelPlanning {
    modelId: number;
    baselineOffers: number | null;
    offersToday: number | null;
    offersCurrentWeek: number | null;
    offersPreviousFullWeek: number | null;
    negotiationsStartedCurrentWeek: number;
    negotiationsStartedPreviousFullWeek: number;
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

export interface ModelPlanning {
    modelId: number;
    baselineOffers: number | null;
    offersLast24Hours: number | null;
    offersLast7Days: number | null;
    negotiationsStartedToday: number;
    negotiationsStartedLast7Days: number;
    recommendedBots: number | null;
    existingBots: number;
    statsReady: boolean;
    trackedDays: number;
    lastStatsUpdatedAt: string | null;
    lastScanComplete: boolean;
}

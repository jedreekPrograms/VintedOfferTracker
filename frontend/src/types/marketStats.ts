export interface ModelPlanning {
    modelId: number;
    offersLast7Days: number | null;
    recommendedBots: number | null;
    existingBots: number;
    statsReady: boolean;
    trackedDays: number;
    lastStatsUpdatedAt: string | null;
    lastScanComplete: boolean;
}

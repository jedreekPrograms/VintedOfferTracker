export type DashboardPeriod =
    | "TODAY"
    | "LAST_7_DAYS"
    | "LAST_30_DAYS"
    | "ALL";

export interface DashboardStatsResponse {
    activeBotsCount: number;
    negotiatingCount: number;
    actionRequiredCount: number;
    purchasedCount: number;
    skippedByUserCount: number;
    totalSpent: number;
    totalNegotiatedSavings: number;
    averagePurchasePrice: number;
    averageDiscountPercentage: number;
}

export type RuntimeStatus =
    | "IDLE"
    | "QUEUED"
    | "WORKING"
    | "COOLDOWN"
    | "ERROR";

export interface RuntimeDashboardBot {
    botId: number;
    name: string;
    botStatus: string;
    runtimeStatus: RuntimeStatus;
    lastRunStartedAt: string | null;
    lastRunFinishedAt: string | null;
    nextRunAt: string | null;
    lastRunDurationMs: number | null;
    consecutiveFailures: number;
    lastError: string | null;
    workerSlot: number | null;
    updatedAt: string | null;
}

export interface RuntimeDashboardResponse {
    totalBots: number;
    runningBots: number;
    idleCount: number;
    queuedCount: number;
    workingCount: number;
    cooldownCount: number;
    errorCount: number;
    averageLastRunDurationMs: number;
    bots: RuntimeDashboardBot[];
}

export async function getDashboardStats(
    period: DashboardPeriod,
): Promise<DashboardStatsResponse> {
    const response = await fetch(
        `/api/dashboard/stats?period=${encodeURIComponent(period)}`,
    );

    if (!response.ok) {
        throw new Error(
            `Nie udało się pobrać statystyk dashboardu. Status HTTP: ${response.status}.`,
        );
    }

    return response.json() as Promise<DashboardStatsResponse>;
}

export async function getRuntimeDashboard(): Promise<RuntimeDashboardResponse> {
    const response = await fetch(
        "/api/dashboard/runtime",
    );

    if (!response.ok) {
        throw new Error(
            `Nie udało się pobrać stanu runtime. Status HTTP: ${response.status}.`,
        );
    }

    return response.json() as Promise<RuntimeDashboardResponse>;
}

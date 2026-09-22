import {
    assertApiResponse,
} from "./apiError";
import type {
    HistoryOutcome,
    OfferAssessment,
} from "./historyApi";

export type DashboardPeriod =
    | "TODAY"
    | "LAST_7_DAYS"
    | "LAST_30_DAYS"
    | "THIS_MONTH"
    | "THIS_YEAR"
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
    selectedHistoryCount: number;
    missedOpportunityCount: number;
    legitCount: number;
    scamCount: number;
    unassessedCount: number;
    averageSelectedPrice: number | null;
    medianSelectedPrice: number | null;
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
    sessionBlockedSince: string | null;
    sessionBlockCount: number;
    sessionPreviewRequested: boolean;
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
    outcomes: HistoryOutcome[] = [],
    assessments: OfferAssessment[] = [],
): Promise<DashboardStatsResponse> {
    const params = new URLSearchParams();
    params.set("period", period);

    for (const outcome of outcomes) {
        params.append("outcomes", outcome);
    }

    for (const assessment of assessments) {
        params.append("assessments", assessment);
    }

    const response = await fetch(
        `/api/dashboard/stats?${params.toString()}`,
    );

    await assertApiResponse(
        response,
        `Nie udało się pobrać statystyk dashboardu. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<DashboardStatsResponse>;
}

export async function getRuntimeDashboard(): Promise<RuntimeDashboardResponse> {
    const response = await fetch("/api/dashboard/runtime");

    await assertApiResponse(
        response,
        `Nie udało się pobrać stanu runtime. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<RuntimeDashboardResponse>;
}

export async function setRuntimeSessionPreview(
    botId: number,
    enabled: boolean,
): Promise<void> {
    const response = await fetch(
        `/api/dashboard/runtime/${encodeURIComponent(botId)}/session-preview?enabled=${enabled}`,
        {
            method: "PUT",
        },
    );

    await assertApiResponse(
        response,
        `Nie udało się ${enabled ? "włączyć" : "wyłączyć"} podglądu sesji. Status HTTP: ${response.status}.`,
    );
}

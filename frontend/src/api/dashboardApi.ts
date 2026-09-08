import {
    assertApiResponse,
} from "./apiError";

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
    | "CAPTCHA_REQUIRED"
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
    captchaRequiredSince: string | null;
    captchaRecoveryRequestedAt: string | null;
    updatedAt: string | null;
}

export interface RuntimeDashboardResponse {
    totalBots: number;
    runningBots: number;
    idleCount: number;
    queuedCount: number;
    workingCount: number;
    cooldownCount: number;
    captchaRequiredCount: number;
    errorCount: number;
    averageLastRunDurationMs: number;
    bots: RuntimeDashboardBot[];
}

export type CaptchaControlStatus =
    | "IDLE"
    | "PREPARING"
    | "READY"
    | "HOLDING"
    | "COMPLETED"
    | "FAILED";

export interface CaptchaControlState {
    botId: number;
    status: CaptchaControlStatus;
    updatedAt: string | null;
    holdHeartbeatAt: string | null;
    message: string | null;
}

export async function getDashboardStats(
    period: DashboardPeriod,
): Promise<DashboardStatsResponse> {
    const response = await fetch(
        `/api/dashboard/stats?period=${encodeURIComponent(period)}`,
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

export async function requestCaptchaRecovery(botId: number): Promise<void> {
    const response = await fetch(
        `/api/bots/${botId}/runtime/captcha-recovery`,
        { method: "POST" },
    );

    await assertApiResponse(
        response,
        `Nie udało się otworzyć przeglądarki CAPTCHA dla bota #${botId}. Status HTTP: ${response.status}.`,
    );
}

export async function getCaptchaControlState(
    botId: number,
): Promise<CaptchaControlState> {
    const response = await fetch(
        `/api/bots/${botId}/runtime/captcha-control`,
    );

    await assertApiResponse(
        response,
        `Nie udało się pobrać stanu sterowania CAPTCHA dla bota #${botId}. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<CaptchaControlState>;
}

export async function startCaptchaHold(
    botId: number,
): Promise<CaptchaControlState> {
    return postCaptchaControl(botId, "hold/start");
}

export async function heartbeatCaptchaHold(
    botId: number,
): Promise<CaptchaControlState> {
    return postCaptchaControl(botId, "hold/heartbeat");
}

export async function endCaptchaHold(
    botId: number,
): Promise<CaptchaControlState> {
    return postCaptchaControl(botId, "hold/end");
}

async function postCaptchaControl(
    botId: number,
    action: string,
): Promise<CaptchaControlState> {
    const response = await fetch(
        `/api/bots/${botId}/runtime/captcha-control/${action}`,
        { method: "POST" },
    );

    await assertApiResponse(
        response,
        `Nie udało się wysłać sterowania CAPTCHA dla bota #${botId}. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<CaptchaControlState>;
}

import { assertApiResponse } from "./apiError";

export type DashboardPeriod = "TODAY" | "LAST_7_DAYS" | "LAST_30_DAYS" | "ALL";

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

export type RuntimeStatus = "IDLE" | "QUEUED" | "WORKING" | "COOLDOWN" | "CAPTCHA_REQUIRED" | "ERROR";

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

export interface RemoteCaptchaState {
    active: boolean;
    botId: number | null;
    screenshotAvailable: boolean;
    frameVersion: number;
    viewportWidth: number;
    viewportHeight: number;
}

export type RemotePointerType = "DOWN" | "MOVE" | "UP";

export async function getDashboardStats(period: DashboardPeriod): Promise<DashboardStatsResponse> {
    const response = await fetch(`/api/dashboard/stats?period=${encodeURIComponent(period)}`);
    await assertApiResponse(response, `Nie udało się pobrać statystyk dashboardu. Status HTTP: ${response.status}.`);
    return response.json() as Promise<DashboardStatsResponse>;
}

export async function getRuntimeDashboard(): Promise<RuntimeDashboardResponse> {
    const response = await fetch("/api/dashboard/runtime");
    await assertApiResponse(response, `Nie udało się pobrać stanu runtime. Status HTTP: ${response.status}.`);
    return response.json() as Promise<RuntimeDashboardResponse>;
}

export async function requestCaptchaRecovery(botId: number): Promise<void> {
    const response = await fetch(`/api/bots/${botId}/runtime/captcha-recovery`, { method: "POST" });
    await assertApiResponse(response, `Nie udało się otworzyć przeglądarki CAPTCHA dla bota #${botId}. Status HTTP: ${response.status}.`);
}

export async function getRemoteCaptchaState(botId: number): Promise<RemoteCaptchaState> {
    const response = await fetch(`/api/bots/${botId}/runtime/captcha-remote/state`, { cache: "no-store" });
    await assertApiResponse(response, `Nie udało się pobrać zdalnej sesji CAPTCHA. Status HTTP: ${response.status}.`);
    return response.json() as Promise<RemoteCaptchaState>;
}

export async function getRemoteCaptchaScreenshot(botId: number, frameVersion: number): Promise<Blob> {
    const response = await fetch(
        `/api/bots/${botId}/runtime/captcha-remote/screenshot?v=${frameVersion}`,
        { cache: "no-store" },
    );
    await assertApiResponse(response, `Nie udało się pobrać podglądu CAPTCHA. Status HTTP: ${response.status}.`);
    return response.blob();
}

export async function sendRemoteCaptchaPointer(
    botId: number,
    type: RemotePointerType,
    x: number,
    y: number,
): Promise<void> {
    const response = await fetch(`/api/bots/${botId}/runtime/captcha-remote/pointer`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ type, x, y }),
    });
    await assertApiResponse(response, `Nie udało się przekazać gestu do sesji CAPTCHA. Status HTTP: ${response.status}.`);
}

import type {
    ModelPlanning,
} from "../types/marketStats";
import {
    assertApiResponse,
} from "./apiError";

const MARKET_STATS_BASE_URL = "/api/market-stats";

export async function getModelPlanning(): Promise<ModelPlanning[]> {
    const response = await fetch(`${MARKET_STATS_BASE_URL}/planning`);

    await assertApiResponse(
        response,
        `Nie udało się pobrać statystyk modeli. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<ModelPlanning[]>;
}


export type MarketStatsHealthStatus =
    | "IDLE"
    | "WAITING"
    | "WARMING_UP"
    | "OK"
    | "PARTIAL"
    | "STALE";

export interface MarketStatsHealth {
    status: MarketStatsHealthStatus;
    totalModels: number;
    baselineReadyModels: number;
    pendingBaselineModels: number;
    incompleteModels: number;
    lastScanAt: string | null;
    lastSuccessfulScanAt: string | null;
}

export async function getMarketStatsHealth(): Promise<MarketStatsHealth> {
    const response = await fetch(`${MARKET_STATS_BASE_URL}/health`);

    await assertApiResponse(
        response,
        `Nie udało się pobrać stanu Observera. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<MarketStatsHealth>;
}

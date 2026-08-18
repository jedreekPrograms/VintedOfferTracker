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

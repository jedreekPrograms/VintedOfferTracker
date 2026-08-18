import {
    assertApiResponse,
} from "./apiError";

export interface MarketStatsObserver {
    id: number;
    name: string;
    email: string;
}

export interface CreateMarketStatsObserverRequest {
    name: string;
    email: string;
    password: string;
}

export interface UpdateMarketStatsObserverRequest {
    name: string;
    email: string;
    password: string | null;
}

const OBSERVER_URL = "/api/market-stats/observer";

export async function getMarketStatsObserver(): Promise<MarketStatsObserver | null> {
    const response = await fetch(OBSERVER_URL);

    if (response.status === 204) {
        return null;
    }

    await assertApiResponse(
        response,
        `Nie udało się pobrać observera. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<MarketStatsObserver>;
}

export async function createMarketStatsObserver(
    request: CreateMarketStatsObserverRequest,
): Promise<MarketStatsObserver> {
    const response = await fetch(OBSERVER_URL, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    await assertApiResponse(
        response,
        `Nie udało się utworzyć observera. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<MarketStatsObserver>;
}

export async function updateMarketStatsObserver(
    request: UpdateMarketStatsObserverRequest,
): Promise<MarketStatsObserver> {
    const response = await fetch(OBSERVER_URL, {
        method: "PATCH",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    await assertApiResponse(
        response,
        `Nie udało się zapisać observera. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<MarketStatsObserver>;
}

export async function deleteMarketStatsObserver(): Promise<void> {
    const response = await fetch(OBSERVER_URL, {
        method: "DELETE",
    });

    await assertApiResponse(
        response,
        `Nie udało się usunąć observera. Status HTTP: ${response.status}.`,
    );
}

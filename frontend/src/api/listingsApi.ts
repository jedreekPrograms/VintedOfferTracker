import type {
    ActionRequiredListing,
    Listing,
} from "../types/listings";
import {
    assertApiResponse,
} from "./apiError";

const BOTS_BASE_URL = "/api/bots";

export async function getAllActionRequiredListings():
Promise<ActionRequiredListing[]> {
    const response = await fetch(
        "/api/listings/action-required",
    );

    await assertApiResponse(
        response,
        `Nie udało się pobrać ofert do kupienia. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<ActionRequiredListing[]>;
}

export async function getActionRequiredCount(): Promise<number> {
    const response = await fetch(
        "/api/listings/action-required/count",
    );

    await assertApiResponse(
        response,
        `Nie udało się pobrać liczby ofert do kupienia. Status HTTP: ${response.status}.`,
    );

    const body = await response.json() as {
        count: number;
    };

    return body.count;
}

export async function getActionRequiredListings(
    botId: number,
): Promise<Listing[]> {
    const response = await fetch(
        `${BOTS_BASE_URL}/${botId}/listings/action-required`,
    );

    if (response.status === 404) {
        throw new Error(`Nie znaleziono bota o ID ${botId}.`);
    }

    await assertApiResponse(
        response,
        `Nie udało się pobrać ofert do kupienia dla bota ${botId}. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<Listing[]>;
}

export async function markListingAsPurchased(
    botId: number,
    listingId: number,
): Promise<void> {
    const response = await fetch(
        `/api/bots/${botId}/listings/${listingId}/purchased`,
        { method: "PATCH" },
    );

    await assertApiResponse(
        response,
        `Nie udało się oznaczyć oferty jako kupione. HTTP ${response.status}`,
    );
}

export async function skipListingByUser(
    botId: number,
    listingId: number,
): Promise<void> {
    const response = await fetch(
        `/api/bots/${botId}/listings/${listingId}/skip`,
        { method: "PATCH" },
    );

    await assertApiResponse(
        response,
        `Nie udało się odrzucić oferty. HTTP ${response.status}`,
    );
}

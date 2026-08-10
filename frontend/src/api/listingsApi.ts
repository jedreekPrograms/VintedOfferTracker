import type {
    Listing,
} from "../types/listings";

const BOTS_BASE_URL =
    "/api/bots";

export async function getActionRequiredListings(
    botId: number,
): Promise<Listing[]> {
    const response =
        await fetch(
            `${BOTS_BASE_URL}/${botId}/listings/action-required`,
        );

    if (response.status === 404) {
        throw new Error(
            `Nie znaleziono bota o ID ${botId}.`,
        );
    }

    if (!response.ok) {
        throw new Error(
            `Nie udało się pobrać ofert do kupienia dla bota ${botId}. Status HTTP: ${response.status}.`,
        );
    }

    return response.json() as Promise<Listing[]>;
}

export async function markListingAsPurchased(
    botId: number,
    listingId: number,
): Promise<void> {

    const response =
        await fetch(
            `/api/bots/${botId}/listings/${listingId}/purchased`,
            {
                method: "PATCH",
            },
        );

    if (!response.ok) {

        const responseText =
            await response.text();

        throw new Error(
            responseText.length > 0
                ? responseText
                : `Nie udało się oznaczyć oferty jako kupione. HTTP ${response.status}`,
        );
    }
}

export async function skipListingByUser(
    botId: number,
    listingId: number,
): Promise<void> {

    const response =
        await fetch(
            `/api/bots/${botId}/listings/${listingId}/skip`,
            {
                method: "PATCH",
            },
        );

    if (!response.ok) {

        const responseText =
            await response.text();

        throw new Error(
            responseText.length > 0
                ? responseText
                : `Nie udało się odrzucić oferty. HTTP ${response.status}`,
        );
    }
}
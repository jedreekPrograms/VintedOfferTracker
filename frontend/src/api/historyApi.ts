import {
    assertApiResponse,
} from "./apiError";

export type ListingHistoryStatus =
    | "PURCHASED"
    | "SKIPPED_BY_USER";

export interface ListingHistoryResponse {
    id: number;
    listingId: string;
    title: string;
    url: string;
    originalPrice: number;
    currentPrice: number;
    currentStep: number;
    status: ListingHistoryStatus;
    decisionAt: string | null;
    botId: number;
    botName: string;
}

export async function getListingHistory(): Promise<ListingHistoryResponse[]> {
    const response = await fetch("/api/listings/history");

    await assertApiResponse(
        response,
        `Nie udało się pobrać historii. HTTP ${response.status}`,
    );

    return response.json() as Promise<ListingHistoryResponse[]>;
}

export async function updateHistoryPurchasePrice(
    listingId: number,
    purchasePrice: number,
): Promise<ListingHistoryResponse> {
    const response = await fetch(
        `/api/listings/history/${listingId}/purchase-price`,
        {
            method: "PATCH",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({ purchasePrice }),
        },
    );

    await assertApiResponse(
        response,
        `Nie udało się zmienić ceny zakupu. HTTP ${response.status}`,
    );

    return response.json() as Promise<ListingHistoryResponse>;
}

export async function removeHistoryEntry(listingId: number): Promise<void> {
    const response = await fetch(
        `/api/listings/history/${listingId}`,
        {
            method: "DELETE",
        },
    );

    await assertApiResponse(
        response,
        `Nie udało się usunąć wpisu z historii. HTTP ${response.status}`,
    );
}

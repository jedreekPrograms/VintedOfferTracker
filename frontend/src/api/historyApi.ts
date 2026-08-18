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

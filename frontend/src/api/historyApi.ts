import {
    assertApiResponse,
} from "./apiError";

export type ListingHistoryStatus =
    | "PURCHASED"
    | "SKIPPED_BY_USER"
    | "UNAVAILABLE"
    | "CONTACT_UNAVAILABLE"
    | "REJECTED"
    | "EXPIRED"
    | "FINISHED";

export type HistoryOutcome =
    | "UNCLASSIFIED"
    | "PURCHASED"
    | "REJECTED"
    | "MISSED_OPPORTUNITY";

export type OfferAssessment =
    | "UNASSESSED"
    | "LEGIT"
    | "SCAM";

export type MissedOpportunityReason =
    | "SOLD_BEFORE_PURCHASE"
    | "NO_FUNDS"
    | "TOO_SLOW"
    | "OTHER";

export interface ListingHistoryResponse {
    id: number;
    listingId: string;
    title: string;
    url: string;
    originalPrice: number;
    currentPrice: number;
    currentStep: number;
    status: ListingHistoryStatus;
    historyOutcome: HistoryOutcome;
    offerAssessment: OfferAssessment;
    missedOpportunityReason: MissedOpportunityReason | null;
    decisionAt: string | null;
    botId: number;
    botName: string;
    additionalTargetId: number | null;
    productTargetLabel: string | null;
}

export async function getListingHistory(): Promise<ListingHistoryResponse[]> {
    const response = await fetch("/api/listings/history");

    await assertApiResponse(
        response,
        `Nie udało się pobrać historii. HTTP ${response.status}`,
    );

    return response.json() as Promise<ListingHistoryResponse[]>;
}

export async function updateHistoryClassification(
    listingId: number,
    historyOutcome: HistoryOutcome,
    offerAssessment: OfferAssessment,
    missedOpportunityReason: MissedOpportunityReason | null,
): Promise<ListingHistoryResponse> {
    const response = await fetch(
        `/api/listings/history/${listingId}/classification`,
        {
            method: "PATCH",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify({
                historyOutcome,
                offerAssessment,
                missedOpportunityReason,
            }),
        },
    );

    await assertApiResponse(
        response,
        `Nie udało się zapisać klasyfikacji oferty. HTTP ${response.status}`,
    );

    return response.json() as Promise<ListingHistoryResponse>;
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

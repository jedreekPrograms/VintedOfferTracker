export interface Listing {
    id: number;

    listingId: string;

    title: string;

    url: string;

    originalPrice: number;

    currentPrice: number;

    currentStep: number;

    awaitingSellerResponse: boolean;

    conversationId: string | null;

    conversationUrl: string | null;

    status: string;
}

export interface ActionRequiredListing {
    botId: number;
    botName: string;

    listing: Listing;
}
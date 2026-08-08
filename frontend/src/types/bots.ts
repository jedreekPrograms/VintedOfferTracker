export type Marketplace =
    | "TEST_PLATFORM"
    | "VINTED"
    | "OLX"
    | "ALLEGRO";

export interface CreateNegotiationStepRequest {
    offerPrice: number;
    maxAcceptedCounterOffer: number;
    message: string;
}

export interface CreateBotConfigurationRequest {
    marketplace: Marketplace;

    categoryPath: string[];

    brand: string;
    model: string;

    minPrice: number;
    maxPrice: number;

    dailyNegotiationBudget: number;

    negotiationSteps: CreateNegotiationStepRequest[];
}

export interface CreateBotRequest {
    name: string;

    email: string;
    password: string;

    configuration: CreateBotConfigurationRequest;
}

export interface BotListItem {
    id: number;
    name: string;
    email: string;
    status: string;
}
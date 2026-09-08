export type Marketplace =
    | "TEST_PLATFORM"
    | "VINTED"
    | "OLX"
    | "ALLEGRO";

export type TargetMode =
    | "VINTED_MODEL"
    | "SEARCH_QUERY";

export type NegotiationReactionAction =
    | "NEXT_STEP_NOW"
    | "WAIT_BEFORE_NEXT_STEP";

export interface SellerCounterOfferRuleRequest {
    minimumDiscountPercent: number;
    action: NegotiationReactionAction;
    waitHours: number | null;
}

export interface CreateNegotiationStepRequest {
    offerPrice: number;
    maxAcceptedCounterOffer: number;
    message: string;

    rejectionAction: NegotiationReactionAction;
    rejectionWaitHours: number | null;

    counterOfferDefaultAction: NegotiationReactionAction;
    counterOfferDefaultWaitHours: number | null;

    counterOfferRules: SellerCounterOfferRuleRequest[];
}

export interface CreateBotConfigurationRequest {
    marketplace: Marketplace;

    categoryPath: string[];

    brand: string;

    targetMode: TargetMode;

    model: string | null;

    searchQuery: string | null;

    minPrice: number;
    maxPrice: number;

    autoRaiseOfferToVintedMinimum: boolean;

    maxAutomaticOffer: number | null;

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

export interface BotSellerCounterOfferRule {
    minimumDiscountPercent: number;
    action: NegotiationReactionAction;
    waitHours: number | null;
}

export interface BotNegotiationStep {
    stepNumber: number;

    offerPrice: number;

    maxAcceptedCounterOffer:
        number | null;

    message: string;

    rejectionAction: NegotiationReactionAction;
    rejectionWaitHours: number | null;

    counterOfferDefaultAction: NegotiationReactionAction;
    counterOfferDefaultWaitHours: number | null;

    counterOfferRules: BotSellerCounterOfferRule[];
}

export interface BotConfigurationDetails {
    marketplace: Marketplace;

    categoryPath: string[];

    brand: string;

    targetMode:
        TargetMode | null;

    model:
        string | null;

    searchQuery:
        string | null;

    minPrice: number;

    maxPrice: number;

    autoRaiseOfferToVintedMinimum:
        boolean | null;

    maxAutomaticOffer:
        number | null;

    dailyNegotiationBudget: number;

    negotiationSteps:
        BotNegotiationStep[];
}

export interface BotDetails
    extends BotListItem {

    configuration:
        BotConfigurationDetails;
}

export interface BotEditCapabilities {
    hasActiveNegotiations: boolean;
    minimumNegotiationCap: number | null;
}

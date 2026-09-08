import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../../../types/dictionaries";

import type {
    NegotiationReactionAction,
    TargetMode,
} from "../../../types/bots";

export type NegotiationStepField =
    | "offerPrice"
    | "maxAcceptedCounterOffer"
    | "message";

export type NegotiationStepPolicyField =
    | "rejectionAction"
    | "rejectionWaitHours"
    | "counterOfferDefaultAction"
    | "counterOfferDefaultWaitHours";

export type CounterOfferRuleField =
    | "minimumDiscountPercent"
    | "action"
    | "waitHours";

export interface CounterOfferRuleForm {
    id: number;
    minimumDiscountPercent: string;
    action: NegotiationReactionAction;
    waitHours: string;
}

export interface NegotiationStepForm {
    id: number;
    offerPrice: string;
    maxAcceptedCounterOffer: string;
    message: string;

    rejectionAction: NegotiationReactionAction;
    rejectionWaitHours: string;

    counterOfferDefaultAction: NegotiationReactionAction;
    counterOfferDefaultWaitHours: string;

    counterOfferRules: CounterOfferRuleForm[];
}

export interface CreateBotFormValues {
    botName: string;

    email: string;
    password: string;

    selectedCategoryId: string;
    selectedBrandId: string;

    targetMode: TargetMode;

    selectedModelId: string;

    searchQuery: string;

    minPrice: string;
    maxPrice: string;

    autoRaiseOfferToVintedMinimum: boolean;

    maxAutomaticOffer: string;

    dailyNegotiationBudget: string;

    negotiationSteps: NegotiationStepForm[];
}

export interface ValidatedCounterOfferRule {
    minimumDiscountPercent: number;
    action: NegotiationReactionAction;
    waitHours: number | null;
}

export interface ValidatedNegotiationStep {
    offerPrice: number;
    maxAcceptedCounterOffer: number;
    message: string;

    rejectionAction: NegotiationReactionAction;
    rejectionWaitHours: number | null;

    counterOfferDefaultAction: NegotiationReactionAction;
    counterOfferDefaultWaitHours: number | null;

    counterOfferRules: ValidatedCounterOfferRule[];
}

export interface ValidatedCreateBotForm {
    name: string;

    email: string;
    password: string;

    category: DictionaryCategory;
    brand: DictionaryBrand;

    targetMode: TargetMode;

    model: DictionaryModel | null;

    searchQuery: string | null;

    minPrice: number;
    maxPrice: number;

    autoRaiseOfferToVintedMinimum: boolean;

    maxAutomaticOffer: number | null;

    dailyNegotiationBudget: number;

    negotiationSteps: ValidatedNegotiationStep[];
}

export type CreateBotFormValidationResult =
    | {
        valid: true;
        data: ValidatedCreateBotForm;
    }
    | {
        valid: false;
        errorMessage: string;
    };

import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../../../types/dictionaries";

export type NegotiationStepField =
    | "offerPrice"
    | "maxAcceptedCounterOffer"
    | "message";

export interface NegotiationStepForm {
    id: number;
    offerPrice: string;
    maxAcceptedCounterOffer: string;
    message: string;
}

export interface CreateBotFormValues {
    botName: string;

    email: string;
    password: string;

    selectedCategoryId: string;
    selectedBrandId: string;
    selectedModelId: string;

    minPrice: string;
    maxPrice: string;

    dailyNegotiationBudget: string;

    negotiationSteps: NegotiationStepForm[];
}

export interface ValidatedNegotiationStep {
    offerPrice: number;
    maxAcceptedCounterOffer: number;
    message: string;
}

export interface ValidatedCreateBotForm {
    name: string;

    email: string;
    password: string;

    category: DictionaryCategory;
    brand: DictionaryBrand;
    model: DictionaryModel;

    minPrice: number;
    maxPrice: number;

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
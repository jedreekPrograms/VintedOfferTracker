import type {
    TargetMode,
} from "./bots";

export interface DictionaryBrand {
    id: number;
    name: string;
}

export interface CreateDictionaryBrandRequest {
    name: string;
}

export interface DictionaryCategory {
    id: number;
    name: string;
    path: string;
    categoryPath: string[];
}

export interface CreateDictionaryCategoryRequest {
    categoryPath: string[];
}

export interface DictionaryModel {
    id: number;
    name: string;
    brandId: number;
    brandName: string;
    targetMode: TargetMode;
    proposedOfferPrice: number | null;
    expectedResalePrice: number | null;
}

export interface CreateDictionaryModelRequest {
    name: string;
    targetMode?: TargetMode;
}

export interface UpdateDictionaryModelPricingRequest {
    proposedOfferPrice: number | null;
    expectedResalePrice: number | null;
}

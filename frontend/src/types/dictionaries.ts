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
}

export interface CreateDictionaryModelRequest {
    name: string;
}
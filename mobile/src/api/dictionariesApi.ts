import type {
    CreateDictionaryBrandRequest,
    CreateDictionaryCategoryRequest,
    CreateDictionaryModelRequest,
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
    UpdateDictionaryModelCategoryRequest,
    UpdateDictionaryModelPricingRequest,
} from "../types/dictionaries";
import {
    assertApiResponse,
} from "./apiError";

const DICTIONARIES_BASE_URL = "/api/dictionaries";

export async function getBrands(): Promise<DictionaryBrand[]> {
    const response = await fetch(`${DICTIONARIES_BASE_URL}/brands`);

    await assertApiResponse(
        response,
        `Nie udało się pobrać marek. Status HTTP: ${response.status}`,
    );

    return response.json() as Promise<DictionaryBrand[]>;
}

export async function createBrand(
    request: CreateDictionaryBrandRequest,
): Promise<DictionaryBrand> {
    const response = await fetch(`${DICTIONARIES_BASE_URL}/brands`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    if (response.status === 409) {
        throw new Error(`Marka „${request.name}” już istnieje.`);
    }

    if (response.status === 400) {
        throw new Error("Nazwa marki jest nieprawidłowa.");
    }

    await assertApiResponse(
        response,
        `Nie udało się dodać marki. Status HTTP: ${response.status}`,
    );

    return response.json() as Promise<DictionaryBrand>;
}

export async function updateBrand(
    brandId: number,
    request: CreateDictionaryBrandRequest,
): Promise<DictionaryBrand> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands/${brandId}`,
        {
            method: "PATCH",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    await assertApiResponse(
        response,
        `Nie udało się zmienić marki. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<DictionaryBrand>;
}

export async function deleteBrand(brandId: number): Promise<void> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands/${brandId}`,
        { method: "DELETE" },
    );

    await assertApiResponse(
        response,
        `Nie udało się usunąć marki. Status HTTP: ${response.status}.`,
    );
}

export async function getCategories(): Promise<DictionaryCategory[]> {
    const response = await fetch(`${DICTIONARIES_BASE_URL}/categories`);

    await assertApiResponse(
        response,
        `Nie udało się pobrać kategorii. Status HTTP: ${response.status}`,
    );

    return response.json() as Promise<DictionaryCategory[]>;
}

export async function createCategory(
    request: CreateDictionaryCategoryRequest,
): Promise<DictionaryCategory> {
    const response = await fetch(`${DICTIONARIES_BASE_URL}/categories`, {
        method: "POST",
        headers: {
            "Content-Type": "application/json",
        },
        body: JSON.stringify(request),
    });

    if (response.status === 409) {
        throw new Error(
            `Kategoria „${request.categoryPath.join(" > ")}” już istnieje.`,
        );
    }

    if (response.status === 400) {
        throw new Error("Ścieżka kategorii jest nieprawidłowa.");
    }

    await assertApiResponse(
        response,
        `Nie udało się dodać kategorii. Status HTTP: ${response.status}`,
    );

    return response.json() as Promise<DictionaryCategory>;
}

export async function updateCategory(
    categoryId: number,
    request: CreateDictionaryCategoryRequest,
): Promise<DictionaryCategory> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/categories/${categoryId}`,
        {
            method: "PATCH",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    await assertApiResponse(
        response,
        `Nie udało się zmienić kategorii. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<DictionaryCategory>;
}

export async function deleteCategory(categoryId: number): Promise<void> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/categories/${categoryId}`,
        { method: "DELETE" },
    );

    await assertApiResponse(
        response,
        `Nie udało się usunąć kategorii. Status HTTP: ${response.status}.`,
    );
}

export async function getModelsByBrand(
    brandId: number,
): Promise<DictionaryModel[]> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands/${brandId}/models`,
    );

    if (response.status === 404) {
        throw new Error("Wybrana marka nie istnieje.");
    }

    await assertApiResponse(
        response,
        `Nie udało się pobrać modeli. Status HTTP: ${response.status}`,
    );

    return response.json() as Promise<DictionaryModel[]>;
}

export async function createModel(
    brandId: number,
    request: CreateDictionaryModelRequest,
): Promise<DictionaryModel> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands/${brandId}/models`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    if (response.status === 404) {
        throw new Error("Wybrana marka lub kategoria nie istnieje.");
    }

    if (response.status === 409) {
        throw new Error(
            `Model „${request.name}” już istnieje dla wybranej marki.`,
        );
    }

    if (response.status === 400) {
        throw new Error(
            "Nazwa, kategoria lub sposób wyszukiwania modelu jest nieprawidłowy.",
        );
    }

    await assertApiResponse(
        response,
        `Nie udało się dodać modelu. Status HTTP: ${response.status}`,
    );

    return response.json() as Promise<DictionaryModel>;
}

export async function updateModel(
    brandId: number,
    modelId: number,
    request: CreateDictionaryModelRequest,
): Promise<DictionaryModel> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands/${brandId}/models/${modelId}`,
        {
            method: "PATCH",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    await assertApiResponse(
        response,
        `Nie udało się zmienić modelu. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<DictionaryModel>;
}

export async function updateModelCategory(
    brandId: number,
    modelId: number,
    request: UpdateDictionaryModelCategoryRequest,
): Promise<DictionaryModel> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands/${brandId}/models/${modelId}/category`,
        {
            method: "PATCH",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    await assertApiResponse(
        response,
        `Nie udało się zmienić kategorii modelu. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<DictionaryModel>;
}

export async function updateModelPricing(
    brandId: number,
    modelId: number,
    request: UpdateDictionaryModelPricingRequest,
): Promise<DictionaryModel> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands/${brandId}/models/${modelId}/pricing`,
        {
            method: "PATCH",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    await assertApiResponse(
        response,
        `Nie udało się zapisać cen referencyjnych modelu. Status HTTP: ${response.status}.`,
    );

    return response.json() as Promise<DictionaryModel>;
}

export async function deleteModel(
    brandId: number,
    modelId: number,
): Promise<void> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands/${brandId}/models/${modelId}`,
        { method: "DELETE" },
    );

    await assertApiResponse(
        response,
        `Nie udało się usunąć modelu. Status HTTP: ${response.status}.`,
    );
}

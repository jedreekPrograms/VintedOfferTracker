import type {
    CreateDictionaryBrandRequest,
    CreateDictionaryCategoryRequest,
    DictionaryBrand,
    DictionaryCategory,
} from "../types/dictionaries";

const DICTIONARIES_BASE_URL = "/api/dictionaries";

export async function getBrands(): Promise<DictionaryBrand[]> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands`,
    );

    if (!response.ok) {
        throw new Error(
            `Nie udało się pobrać marek. Status HTTP: ${response.status}`,
        );
    }

    return response.json() as Promise<DictionaryBrand[]>;
}

export async function createBrand(
    request: CreateDictionaryBrandRequest,
): Promise<DictionaryBrand> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    if (response.status === 409) {
        throw new Error(
            `Marka „${request.name}” już istnieje.`,
        );
    }

    if (response.status === 400) {
        throw new Error(
            "Nazwa marki jest nieprawidłowa.",
        );
    }

    if (!response.ok) {
        throw new Error(
            `Nie udało się dodać marki. Status HTTP: ${response.status}`,
        );
    }

    return response.json() as Promise<DictionaryBrand>;
}

export async function getCategories(): Promise<DictionaryCategory[]> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/categories`,
    );

    if (!response.ok) {
        throw new Error(
            `Nie udało się pobrać kategorii. Status HTTP: ${response.status}`,
        );
    }

    return response.json() as Promise<DictionaryCategory[]>;
}

export async function createCategory(
    request: CreateDictionaryCategoryRequest,
): Promise<DictionaryCategory> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/categories`,
        {
            method: "POST",
            headers: {
                "Content-Type": "application/json",
            },
            body: JSON.stringify(request),
        },
    );

    if (response.status === 409) {
        throw new Error(
            `Kategoria „${request.categoryPath.join(" > ")}” już istnieje.`,
        );
    }

    if (response.status === 400) {
        throw new Error(
            "Ścieżka kategorii jest nieprawidłowa.",
        );
    }

    if (!response.ok) {
        throw new Error(
            `Nie udało się dodać kategorii. Status HTTP: ${response.status}`,
        );
    }

    return response.json() as Promise<DictionaryCategory>;
}
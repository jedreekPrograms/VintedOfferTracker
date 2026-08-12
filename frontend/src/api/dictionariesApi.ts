import type {
    CreateDictionaryBrandRequest,
    CreateDictionaryCategoryRequest,
    CreateDictionaryModelRequest,
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../types/dictionaries";

const DICTIONARIES_BASE_URL =
    "/api/dictionaries";


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

    return readDictionaryResponse(
        response,
        `Nie udało się dodać marki.`,
    );
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

    return readDictionaryResponse(
        response,
        "Nie udało się zmienić marki.",
    );
}


export async function deleteBrand(
    brandId: number,
): Promise<void> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands/${brandId}`,
        {
            method: "DELETE",
        },
    );

    await ensureDictionaryMutationSucceeded(
        response,
        "Nie udało się usunąć marki.",
    );
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

    return readDictionaryResponse(
        response,
        "Nie udało się dodać kategorii.",
    );
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

    return readDictionaryResponse(
        response,
        "Nie udało się zmienić kategorii.",
    );
}


export async function deleteCategory(
    categoryId: number,
): Promise<void> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/categories/${categoryId}`,
        {
            method: "DELETE",
        },
    );

    await ensureDictionaryMutationSucceeded(
        response,
        "Nie udało się usunąć kategorii.",
    );
}


export async function getModelsByBrand(
    brandId: number,
): Promise<DictionaryModel[]> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands/${brandId}/models`,
    );

    if (!response.ok) {
        throw new Error(
            await getApiErrorMessage(
                response,
                `Nie udało się pobrać modeli. Status HTTP: ${response.status}`,
            ),
        );
    }

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

    return readDictionaryResponse(
        response,
        "Nie udało się dodać modelu.",
    );
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

    return readDictionaryResponse(
        response,
        "Nie udało się zmienić modelu.",
    );
}


export async function deleteModel(
    brandId: number,
    modelId: number,
): Promise<void> {
    const response = await fetch(
        `${DICTIONARIES_BASE_URL}/brands/${brandId}/models/${modelId}`,
        {
            method: "DELETE",
        },
    );

    await ensureDictionaryMutationSucceeded(
        response,
        "Nie udało się usunąć modelu.",
    );
}


async function readDictionaryResponse<T>(
    response: Response,
    fallbackMessage: string,
): Promise<T> {
    if (!response.ok) {
        throw new Error(
            await getApiErrorMessage(
                response,
                fallbackMessage,
            ),
        );
    }

    return response.json() as Promise<T>;
}


async function ensureDictionaryMutationSucceeded(
    response: Response,
    fallbackMessage: string,
): Promise<void> {
    if (!response.ok) {
        throw new Error(
            await getApiErrorMessage(
                response,
                fallbackMessage,
            ),
        );
    }
}


async function getApiErrorMessage(
    response: Response,
    fallbackMessage: string,
): Promise<string> {
    try {
        const body = await response.json() as {
            message?: unknown;
        };

        if (
            typeof body.message === "string"
            && body.message.trim().length > 0
        ) {
            return body.message;
        }
    } catch {
        // Odpowiedź nie musi zawierać JSON-a.
    }

    return fallbackMessage;
}

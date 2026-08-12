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

    await assertMutationResponse(
        response,
        "Nie udało się zmienić marki.",
    );

    return response.json() as Promise<DictionaryBrand>;
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

    await assertMutationResponse(
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

    await assertMutationResponse(
        response,
        "Nie udało się zmienić kategorii.",
    );

    return response.json() as Promise<DictionaryCategory>;
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

    await assertMutationResponse(
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

    if (response.status === 404) {
        throw new Error(
            "Wybrana marka nie istnieje.",
        );
    }

    if (!response.ok) {
        throw new Error(
            `Nie udało się pobrać modeli. Status HTTP: ${response.status}`,
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

    if (response.status === 404) {
        throw new Error(
            "Wybrana marka nie istnieje.",
        );
    }

    if (response.status === 409) {
        throw new Error(
            `Model „${request.name}” już istnieje dla wybranej marki.`,
        );
    }

    if (response.status === 400) {
        throw new Error(
            "Nazwa modelu jest nieprawidłowa.",
        );
    }

    if (!response.ok) {
        throw new Error(
            `Nie udało się dodać modelu. Status HTTP: ${response.status}`,
        );
    }

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

    await assertMutationResponse(
        response,
        "Nie udało się zmienić modelu.",
    );

    return response.json() as Promise<DictionaryModel>;
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

    await assertMutationResponse(
        response,
        "Nie udało się usunąć modelu.",
    );
}


async function assertMutationResponse(
    response: Response,
    fallbackMessage: string,
): Promise<void> {
    if (response.ok) {
        return;
    }

    let backendMessage: string | null =
        null;

    try {
        const body = await response.json() as {
            message?: unknown;
        };

        if (
            typeof body.message === "string"
            && body.message.trim().length > 0
        ) {
            backendMessage = body.message;
        }
    } catch {
        backendMessage = null;
    }

    if (backendMessage !== null) {
        throw new Error(backendMessage);
    }

    throw new Error(
        `${fallbackMessage} Status HTTP: ${response.status}.`,
    );
}

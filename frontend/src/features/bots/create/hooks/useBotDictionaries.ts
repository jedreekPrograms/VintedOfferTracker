import {
    useCallback,
    useEffect,
    useState,
} from "react";

import {
    getBrands,
    getCategories,
    getModelsByBrand,
} from "../../../../api/dictionariesApi";

import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../../../../types/dictionaries";

interface UseBotDictionariesResult {
    categories: DictionaryCategory[];
    brands: DictionaryBrand[];
    models: DictionaryModel[];

    isLoadingDictionaries: boolean;
    areModelsLoading: boolean;

    modelsBrandId: string | null;

    dictionaryErrorMessage: string | null;

    reloadDictionaries: () => Promise<void>;
    reloadModels: () => Promise<void>;

    clearDictionaryError: () => void;
}

export function useBotDictionaries(
    selectedBrandId: string,
): UseBotDictionariesResult {
    const [
        categories,
        setCategories,
    ] = useState<DictionaryCategory[]>([]);

    const [
        brands,
        setBrands,
    ] = useState<DictionaryBrand[]>([]);

    const [
        models,
        setModels,
    ] = useState<DictionaryModel[]>([]);

    const [
        isLoadingDictionaries,
        setIsLoadingDictionaries,
    ] = useState(true);

    const [
        areModelsLoading,
        setAreModelsLoading,
    ] = useState(false);

    const [
        modelsBrandId,
        setModelsBrandId,
    ] = useState<string | null>(
        null,
    );

    const [
        dictionaryErrorMessage,
        setDictionaryErrorMessage,
    ] = useState<string | null>(null);

    const loadDictionaries =
        useCallback(
            async () => {
                setIsLoadingDictionaries(
                    true,
                );

                setDictionaryErrorMessage(
                    null,
                );

                try {
                    const [
                        loadedCategories,
                        loadedBrands,
                    ] = await Promise.all([
                        getCategories(),
                        getBrands(),
                    ]);

                    setCategories(
                        loadedCategories,
                    );

                    setBrands(
                        loadedBrands,
                    );
                } catch (error) {
                    setDictionaryErrorMessage(
                        getErrorMessage(
                            error,
                            "Nie udało się pobrać słowników.",
                        ),
                    );
                } finally {
                    setIsLoadingDictionaries(
                        false,
                    );
                }
            },
            [],
        );

    const loadModels =
        useCallback(
            async () => {
                if (
                    selectedBrandId.length === 0
                ) {
                    setModels([]);

                    setModelsBrandId(
                        null,
                    );

                    return;
                }

                const brandId =
                    Number(
                        selectedBrandId,
                    );

                if (
                    !Number.isInteger(brandId)
                    || brandId <= 0
                ) {
                    setModels([]);

                    setModelsBrandId(
                        selectedBrandId,
                    );

                    setDictionaryErrorMessage(
                        "Identyfikator marki jest nieprawidłowy.",
                    );

                    return;
                }

                setAreModelsLoading(
                    true,
                );

                setModelsBrandId(
                    null,
                );

                setDictionaryErrorMessage(
                    null,
                );

                try {
                    const loadedModels =
                        await getModelsByBrand(
                            brandId,
                        );

                    setModels(
                        loadedModels,
                    );

                    setModelsBrandId(
                        selectedBrandId,
                    );
                } catch (error) {
                    setModels([]);

                    setModelsBrandId(
                        selectedBrandId,
                    );

                    setDictionaryErrorMessage(
                        getErrorMessage(
                            error,
                            "Nie udało się pobrać modeli.",
                        ),
                    );
                } finally {
                    setAreModelsLoading(
                        false,
                    );
                }
            },
            [
                selectedBrandId,
            ],
        );

    useEffect(() => {
        void loadDictionaries();
    }, [
        loadDictionaries,
    ]);

    useEffect(() => {
        void loadModels();
    }, [
        loadModels,
    ]);

    function clearDictionaryError() {
        setDictionaryErrorMessage(
            null,
        );
    }

    return {
        categories,
        brands,
        models,

        isLoadingDictionaries,
        areModelsLoading,

        modelsBrandId,

        dictionaryErrorMessage,

        reloadDictionaries:
            loadDictionaries,

        reloadModels:
            loadModels,

        clearDictionaryError,
    };
}

function getErrorMessage(
    error: unknown,
    fallbackMessage: string,
): string {
    if (
        error instanceof Error
    ) {
        return error.message;
    }

    return fallbackMessage;
}

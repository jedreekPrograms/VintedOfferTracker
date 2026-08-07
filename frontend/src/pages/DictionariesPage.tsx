import {
    type FormEvent,
    useCallback,
    useEffect,
    useState,
} from "react";
import {
    createBrand,
    createCategory,
    createModel,
    getBrands,
    getCategories,
    getModelsByBrand,
} from "../api/dictionariesApi";
import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../types/dictionaries";

function DictionariesPage() {
    const [brands, setBrands] =
        useState<DictionaryBrand[]>([]);

    const [brandName, setBrandName] =
        useState("");

    const [areBrandsLoading, setAreBrandsLoading] =
        useState(true);

    const [isBrandSubmitting, setIsBrandSubmitting] =
        useState(false);

    const [brandErrorMessage, setBrandErrorMessage] =
        useState<string | null>(null);

    const [brandSuccessMessage, setBrandSuccessMessage] =
        useState<string | null>(null);

    const [categories, setCategories] =
        useState<DictionaryCategory[]>([]);

    const [categoryPathInput, setCategoryPathInput] =
        useState("");

    const [areCategoriesLoading, setAreCategoriesLoading] =
        useState(true);

    const [isCategorySubmitting, setIsCategorySubmitting] =
        useState(false);

    const [
        categoryErrorMessage,
        setCategoryErrorMessage,
    ] = useState<string | null>(null);

    const [
        categorySuccessMessage,
        setCategorySuccessMessage,
    ] = useState<string | null>(null);

    const [selectedBrandId, setSelectedBrandId] =
        useState("");

    const [models, setModels] =
        useState<DictionaryModel[]>([]);

    const [modelName, setModelName] =
        useState("");

    const [areModelsLoading, setAreModelsLoading] =
        useState(false);

    const [isModelSubmitting, setIsModelSubmitting] =
        useState(false);

    const [modelErrorMessage, setModelErrorMessage] =
        useState<string | null>(null);

    const [modelSuccessMessage, setModelSuccessMessage] =
        useState<string | null>(null);

    const loadBrands = useCallback(async () => {
        setAreBrandsLoading(true);
        setBrandErrorMessage(null);

        try {
            const loadedBrands =
                await getBrands();

            setBrands(
                [...loadedBrands].sort(compareBrands),
            );
        } catch (error) {
            setBrandErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się pobrać marek.",
                ),
            );
        } finally {
            setAreBrandsLoading(false);
        }
    }, []);

    const loadCategories = useCallback(async () => {
        setAreCategoriesLoading(true);
        setCategoryErrorMessage(null);

        try {
            const loadedCategories =
                await getCategories();

            setCategories(
                [...loadedCategories].sort(
                    compareCategories,
                ),
            );
        } catch (error) {
            setCategoryErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się pobrać kategorii.",
                ),
            );
        } finally {
            setAreCategoriesLoading(false);
        }
    }, []);

    const loadModels = useCallback(
        async (brandId: number) => {
            setAreModelsLoading(true);
            setModelErrorMessage(null);

            try {
                const loadedModels =
                    await getModelsByBrand(
                        brandId,
                    );

                setModels(
                    [...loadedModels].sort(
                        compareModels,
                    ),
                );
            } catch (error) {
                setModels([]);

                setModelErrorMessage(
                    getErrorMessage(
                        error,
                        "Nie udało się pobrać modeli.",
                    ),
                );
            } finally {
                setAreModelsLoading(false);
            }
        },
        [],
    );

    useEffect(() => {
        void loadBrands();
        void loadCategories();
    }, [
        loadBrands,
        loadCategories,
    ]);

    useEffect(() => {
        if (brands.length === 0) {
            setSelectedBrandId("");
            setModels([]);

            return;
        }

        const selectedBrandStillExists =
            brands.some(
                (brand) =>
                    String(brand.id)
                    === selectedBrandId,
            );

        if (!selectedBrandStillExists) {
            setSelectedBrandId(
                String(brands[0].id),
            );
        }
    }, [
        brands,
        selectedBrandId,
    ]);

    useEffect(() => {
        if (selectedBrandId.length === 0) {
            setModels([]);

            return;
        }

        void loadModels(
            Number(selectedBrandId),
        );
    }, [
        selectedBrandId,
        loadModels,
    ]);

    async function handleCreateBrand(
        event: FormEvent<HTMLFormElement>,
    ) {
        event.preventDefault();

        const normalizedName =
            normalizeText(
                brandName,
            );

        if (normalizedName.length === 0) {
            setBrandErrorMessage(
                "Wpisz nazwę marki.",
            );

            return;
        }

        setIsBrandSubmitting(true);
        setBrandErrorMessage(null);
        setBrandSuccessMessage(null);

        try {
            const createdBrand =
                await createBrand({
                    name: normalizedName,
                });

            setBrands((currentBrands) =>
                [
                    ...currentBrands,
                    createdBrand,
                ].sort(compareBrands),
            );

            setSelectedBrandId(
                String(createdBrand.id),
            );

            setBrandName("");

            setBrandSuccessMessage(
                `Dodano markę: ${createdBrand.name}.`,
            );
        } catch (error) {
            setBrandErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się dodać marki.",
                ),
            );
        } finally {
            setIsBrandSubmitting(false);
        }
    }

    async function handleCreateCategory(
        event: FormEvent<HTMLFormElement>,
    ) {
        event.preventDefault();

        const categoryPath =
            parseCategoryPath(
                categoryPathInput,
            );

        if (categoryPath.length === 0) {
            setCategoryErrorMessage(
                "Wpisz ścieżkę kategorii.",
            );

            return;
        }

        if (
            categoryPath.some(
                (element) =>
                    element.length === 0,
            )
        ) {
            setCategoryErrorMessage(
                "Każdy element ścieżki kategorii musi mieć nazwę.",
            );

            return;
        }

        if (categoryPath.length > 20) {
            setCategoryErrorMessage(
                "Ścieżka może zawierać maksymalnie 20 elementów.",
            );

            return;
        }

        if (
            categoryPath.some(
                (element) =>
                    element.length > 255,
            )
        ) {
            setCategoryErrorMessage(
                "Pojedyncza nazwa kategorii może mieć maksymalnie 255 znaków.",
            );

            return;
        }

        setIsCategorySubmitting(true);
        setCategoryErrorMessage(null);
        setCategorySuccessMessage(null);

        try {
            const createdCategory =
                await createCategory({
                    categoryPath,
                });

            setCategories(
                (currentCategories) =>
                    [
                        ...currentCategories,
                        createdCategory,
                    ].sort(compareCategories),
            );

            setCategoryPathInput("");

            setCategorySuccessMessage(
                `Dodano kategorię: ${createdCategory.path}.`,
            );
        } catch (error) {
            setCategoryErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się dodać kategorii.",
                ),
            );
        } finally {
            setIsCategorySubmitting(false);
        }
    }

    async function handleCreateModel(
        event: FormEvent<HTMLFormElement>,
    ) {
        event.preventDefault();

        if (selectedBrandId.length === 0) {
            setModelErrorMessage(
                "Najpierw dodaj i wybierz markę.",
            );

            return;
        }

        const normalizedName =
            normalizeText(
                modelName,
            );

        if (normalizedName.length === 0) {
            setModelErrorMessage(
                "Wpisz nazwę modelu.",
            );

            return;
        }

        const brandId =
            Number(selectedBrandId);

        if (!Number.isInteger(brandId)
            || brandId <= 0) {
            setModelErrorMessage(
                "Wybrana marka jest nieprawidłowa.",
            );

            return;
        }

        setIsModelSubmitting(true);
        setModelErrorMessage(null);
        setModelSuccessMessage(null);

        try {
            const createdModel =
                await createModel(
                    brandId,
                    {
                        name: normalizedName,
                    },
                );

            setModels((currentModels) =>
                [
                    ...currentModels,
                    createdModel,
                ].sort(compareModels),
            );

            setModelName("");

            setModelSuccessMessage(
                `Dodano model ${createdModel.name} do marki ${createdModel.brandName}.`,
            );
        } catch (error) {
            setModelErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się dodać modelu.",
                ),
            );
        } finally {
            setIsModelSubmitting(false);
        }
    }

    const selectedBrand =
        brands.find(
            (brand) =>
                String(brand.id)
                === selectedBrandId,
        ) ?? null;

    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">
                        Dane konfiguracyjne
                    </p>

                    <h1 className="page-title">
                        Słowniki
                    </h1>

                    <p className="page-description">
                        Ręcznie zarządzaj kategoriami,
                        markami i modelami używanymi
                        podczas tworzenia botów.
                    </p>
                </div>
            </header>

            <div className="dictionary-management-grid">
                <article className="content-card">
                    <div className="dictionary-section-header">
                        <div>
                            <h2 className="content-card-title">
                                Dodaj markę
                            </h2>

                            <p className="content-card-text">
                                Marka będzie dostępna
                                w formularzu tworzenia bota.
                            </p>
                        </div>

                        <span className="dictionary-count">
                            {brands.length}
                        </span>
                    </div>

                    <form
                        className="dictionary-form"
                        onSubmit={handleCreateBrand}
                    >
                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="brand-name"
                            >
                                Nazwa marki
                            </label>

                            <input
                                id="brand-name"
                                className="form-input"
                                type="text"
                                value={brandName}
                                maxLength={255}
                                placeholder="np. Samsung"
                                disabled={
                                    isBrandSubmitting
                                }
                                onChange={(event) => {
                                    setBrandName(
                                        event.target.value,
                                    );

                                    setBrandErrorMessage(null);
                                    setBrandSuccessMessage(null);
                                }}
                            />
                        </div>

                        <button
                            className="primary-button"
                            type="submit"
                            disabled={
                                isBrandSubmitting
                                || brandName.trim().length === 0
                            }
                        >
                            {isBrandSubmitting
                                ? "Dodawanie..."
                                : "Dodaj markę"}
                        </button>
                    </form>

                    {brandErrorMessage !== null && (
                        <div
                            className="form-message form-message-error"
                            role="alert"
                        >
                            {brandErrorMessage}
                        </div>
                    )}

                    {brandSuccessMessage !== null && (
                        <div
                            className="form-message form-message-success"
                            role="status"
                        >
                            {brandSuccessMessage}
                        </div>
                    )}
                </article>

                <article className="content-card">
                    <div className="dictionary-section-header">
                        <div>
                            <h2 className="content-card-title">
                                Zapisane marki
                            </h2>

                            <p className="content-card-text">
                                Lista marek pobrana
                                bezpośrednio z backendu.
                            </p>
                        </div>

                        <button
                            className="secondary-button"
                            type="button"
                            disabled={areBrandsLoading}
                            onClick={() => {
                                void loadBrands();
                            }}
                        >
                            Odśwież
                        </button>
                    </div>

                    {areBrandsLoading ? (
                        <div className="dictionary-list-state">
                            Pobieranie marek...
                        </div>
                    ) : brands.length === 0 ? (
                        <div className="dictionary-list-state">
                            Nie dodano jeszcze żadnej marki.
                        </div>
                    ) : (
                        <ul className="dictionary-list">
                            {brands.map((brand) => (
                                <li
                                    key={brand.id}
                                    className="dictionary-list-item"
                                >
                                    <div>
                                        <div className="dictionary-item-name">
                                            {brand.name}
                                        </div>

                                        <div className="dictionary-item-id">
                                            ID: {brand.id}
                                        </div>
                                    </div>

                                    <span className="dictionary-item-type">
                                        Marka
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </article>

                <article className="content-card">
                    <div className="dictionary-section-header">
                        <div>
                            <h2 className="content-card-title">
                                Dodaj kategorię
                            </h2>

                            <p className="content-card-text">
                                Wpisz pełną ścieżkę,
                                oddzielając poziomy znakiem
                                większe niż.
                            </p>
                        </div>

                        <span className="dictionary-count">
                            {categories.length}
                        </span>
                    </div>

                    <form
                        className="dictionary-category-form"
                        onSubmit={handleCreateCategory}
                    >
                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="category-path"
                            >
                                Pełna ścieżka kategorii
                            </label>

                            <input
                                id="category-path"
                                className="form-input"
                                type="text"
                                value={categoryPathInput}
                                maxLength={1000}
                                placeholder={
                                    "Elektronika > Telefony komórkowe > Smartfony"
                                }
                                disabled={
                                    isCategorySubmitting
                                }
                                onChange={(event) => {
                                    setCategoryPathInput(
                                        event.target.value,
                                    );

                                    setCategoryErrorMessage(null);
                                    setCategorySuccessMessage(null);
                                }}
                            />

                            <div className="form-help">
                                Przykład: Elektronika
                                &gt; Telefony komórkowe
                                &gt; Smartfony
                            </div>
                        </div>

                        <button
                            className="primary-button"
                            type="submit"
                            disabled={
                                isCategorySubmitting
                                || categoryPathInput
                                    .trim()
                                    .length === 0
                            }
                        >
                            {isCategorySubmitting
                                ? "Dodawanie..."
                                : "Dodaj kategorię"}
                        </button>
                    </form>

                    {categoryErrorMessage !== null && (
                        <div
                            className="form-message form-message-error"
                            role="alert"
                        >
                            {categoryErrorMessage}
                        </div>
                    )}

                    {categorySuccessMessage !== null && (
                        <div
                            className="form-message form-message-success"
                            role="status"
                        >
                            {categorySuccessMessage}
                        </div>
                    )}
                </article>

                <article className="content-card">
                    <div className="dictionary-section-header">
                        <div>
                            <h2 className="content-card-title">
                                Zapisane kategorie
                            </h2>

                            <p className="content-card-text">
                                Kategorie dostępne podczas
                                tworzenia bota.
                            </p>
                        </div>

                        <button
                            className="secondary-button"
                            type="button"
                            disabled={
                                areCategoriesLoading
                            }
                            onClick={() => {
                                void loadCategories();
                            }}
                        >
                            Odśwież
                        </button>
                    </div>

                    {areCategoriesLoading ? (
                        <div className="dictionary-list-state">
                            Pobieranie kategorii...
                        </div>
                    ) : categories.length === 0 ? (
                        <div className="dictionary-list-state">
                            Nie dodano jeszcze żadnej kategorii.
                        </div>
                    ) : (
                        <ul className="dictionary-list">
                            {categories.map(
                                (category) => (
                                    <li
                                        key={category.id}
                                        className="dictionary-list-item"
                                    >
                                        <div className="dictionary-item-content">
                                            <div className="dictionary-item-name">
                                                {category.name}
                                            </div>

                                            <div className="dictionary-item-path">
                                                {category.path}
                                            </div>

                                            <div className="dictionary-item-id">
                                                ID: {category.id}
                                            </div>
                                        </div>

                                        <span className="dictionary-item-type">
                                            Kategoria
                                        </span>
                                    </li>
                                ),
                            )}
                        </ul>
                    )}
                </article>

                <article className="content-card">
                    <div className="dictionary-section-header">
                        <div>
                            <h2 className="content-card-title">
                                Dodaj model
                            </h2>

                            <p className="content-card-text">
                                Każdy model zostanie przypisany
                                wyłącznie do wybranej marki.
                            </p>
                        </div>

                        <span className="dictionary-count">
                            {models.length}
                        </span>
                    </div>

                    {brands.length === 0 ? (
                        <div className="dictionary-list-state">
                            Najpierw dodaj przynajmniej
                            jedną markę.
                        </div>
                    ) : (
                        <form
                            className="dictionary-model-form"
                            onSubmit={handleCreateModel}
                        >
                            <div className="form-field">
                                <label
                                    className="form-label"
                                    htmlFor="model-brand"
                                >
                                    Marka
                                </label>

                                <select
                                    id="model-brand"
                                    className="form-select"
                                    value={selectedBrandId}
                                    disabled={
                                        areBrandsLoading
                                        || isModelSubmitting
                                    }
                                    onChange={(event) => {
                                        setSelectedBrandId(
                                            event.target.value,
                                        );

                                        setModelName("");
                                        setModelErrorMessage(null);
                                        setModelSuccessMessage(null);
                                    }}
                                >
                                    {brands.map((brand) => (
                                        <option
                                            key={brand.id}
                                            value={brand.id}
                                        >
                                            {brand.name}
                                        </option>
                                    ))}
                                </select>
                            </div>

                            <div className="form-field">
                                <label
                                    className="form-label"
                                    htmlFor="model-name"
                                >
                                    Nazwa modelu
                                </label>

                                <input
                                    id="model-name"
                                    className="form-input"
                                    type="text"
                                    value={modelName}
                                    maxLength={255}
                                    placeholder="np. Galaxy S25 Ultra"
                                    disabled={
                                        isModelSubmitting
                                    }
                                    onChange={(event) => {
                                        setModelName(
                                            event.target.value,
                                        );

                                        setModelErrorMessage(null);
                                        setModelSuccessMessage(null);
                                    }}
                                />
                            </div>

                            <button
                                className="primary-button"
                                type="submit"
                                disabled={
                                    isModelSubmitting
                                    || selectedBrandId.length === 0
                                    || modelName.trim().length === 0
                                }
                            >
                                {isModelSubmitting
                                    ? "Dodawanie..."
                                    : "Dodaj model"}
                            </button>
                        </form>
                    )}

                    {modelErrorMessage !== null && (
                        <div
                            className="form-message form-message-error"
                            role="alert"
                        >
                            {modelErrorMessage}
                        </div>
                    )}

                    {modelSuccessMessage !== null && (
                        <div
                            className="form-message form-message-success"
                            role="status"
                        >
                            {modelSuccessMessage}
                        </div>
                    )}
                </article>

                <article className="content-card">
                    <div className="dictionary-section-header">
                        <div>
                            <h2 className="content-card-title">
                                Zapisane modele
                            </h2>

                            <p className="content-card-text">
                                {selectedBrand === null
                                    ? "Wybierz markę."
                                    : `Modele marki ${selectedBrand.name}.`}
                            </p>
                        </div>

                        <button
                            className="secondary-button"
                            type="button"
                            disabled={
                                selectedBrand === null
                                || areModelsLoading
                            }
                            onClick={() => {
                                if (
                                    selectedBrand !== null
                                ) {
                                    void loadModels(
                                        selectedBrand.id,
                                    );
                                }
                            }}
                        >
                            Odśwież
                        </button>
                    </div>

                    {selectedBrand === null ? (
                        <div className="dictionary-list-state">
                            Brak marki do wyświetlenia.
                        </div>
                    ) : areModelsLoading ? (
                        <div className="dictionary-list-state">
                            Pobieranie modeli...
                        </div>
                    ) : models.length === 0 ? (
                        <div className="dictionary-list-state">
                            Marka {selectedBrand.name}
                            nie ma jeszcze zapisanych modeli.
                        </div>
                    ) : (
                        <ul className="dictionary-list">
                            {models.map((model) => (
                                <li
                                    key={model.id}
                                    className="dictionary-list-item"
                                >
                                    <div>
                                        <div className="dictionary-item-name">
                                            {model.name}
                                        </div>

                                        <div className="dictionary-item-path">
                                            Marka: {model.brandName}
                                        </div>

                                        <div className="dictionary-item-id">
                                            ID: {model.id}
                                        </div>
                                    </div>

                                    <span className="dictionary-item-type">
                                        Model
                                    </span>
                                </li>
                            ))}
                        </ul>
                    )}
                </article>
            </div>
        </section>
    );
}

function normalizeText(
    value: string,
): string {
    return value
        .trim()
        .replace(/\s+/g, " ");
}

function parseCategoryPath(
    input: string,
): string[] {
    if (input.trim().length === 0) {
        return [];
    }

    return input
        .split(">")
        .map(normalizeText);
}

function compareBrands(
    firstBrand: DictionaryBrand,
    secondBrand: DictionaryBrand,
): number {
    return firstBrand.name.localeCompare(
        secondBrand.name,
        "pl",
    );
}

function compareCategories(
    firstCategory: DictionaryCategory,
    secondCategory: DictionaryCategory,
): number {
    return firstCategory.path.localeCompare(
        secondCategory.path,
        "pl",
    );
}

function compareModels(
    firstModel: DictionaryModel,
    secondModel: DictionaryModel,
): number {
    return firstModel.name.localeCompare(
        secondModel.name,
        "pl",
    );
}

function getErrorMessage(
    error: unknown,
    fallbackMessage: string,
): string {
    if (error instanceof Error) {
        return error.message;
    }

    return fallbackMessage;
}

export default DictionariesPage;
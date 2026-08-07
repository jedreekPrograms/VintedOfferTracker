import {
    type FormEvent,
    useCallback,
    useEffect,
    useState,
} from "react";
import {
    createBrand,
    createCategory,
    getBrands,
    getCategories,
} from "../api/dictionariesApi";
import type {
    DictionaryBrand,
    DictionaryCategory,
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

    const loadBrands = useCallback(async () => {
        setAreBrandsLoading(true);
        setBrandErrorMessage(null);

        try {
            const loadedBrands = await getBrands();

            setBrands(loadedBrands);
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

            setCategories(loadedCategories);
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

    useEffect(() => {
        void loadBrands();
        void loadCategories();
    }, [
        loadBrands,
        loadCategories,
    ]);

    async function handleCreateBrand(
        event: FormEvent<HTMLFormElement>,
    ) {
        event.preventDefault();

        const normalizedName =
            brandName
                .trim()
                .replace(/\s+/g, " ");

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
                [...currentBrands, createdBrand]
                    .sort(compareBrands),
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
                (element) => element.length === 0,
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
                (element) => element.length > 255,
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

            setCategories((currentCategories) =>
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
                        Ręcznie zarządzaj kategoriami, markami
                        i modelami używanymi podczas tworzenia botów.
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
                                Marka będzie później dostępna
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
                                disabled={isBrandSubmitting}
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
                                Lista marek pobrana z backendu.
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
                                Wpisz pełną ścieżkę, oddzielając
                                poziomy znakiem większe niż.
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
                                Przykład: Elektronika &gt;
                                Telefony komórkowe &gt;
                                Smartfony
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
                                Kategorie będą później dostępne
                                podczas tworzenia bota.
                            </p>
                        </div>

                        <button
                            className="secondary-button"
                            type="button"
                            disabled={areCategoriesLoading}
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
                            {categories.map((category) => (
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
                            ))}
                        </ul>
                    )}
                </article>

                <article className="content-card dictionary-model-placeholder">
                    <h2 className="content-card-title">
                        Modele
                    </h2>

                    <p className="content-card-text">
                        W następnym kroku wybierzesz markę,
                        wpiszesz nazwę modelu i zapiszesz model
                        przypisany wyłącznie do tej marki.
                    </p>

                    <div className="dictionary-example">
                        <span className="dictionary-example-label">
                            Przykład
                        </span>

                        <span>
                            Samsung → Galaxy S25 Ultra
                        </span>
                    </div>
                </article>
            </div>
        </section>
    );
}

function parseCategoryPath(
    input: string,
): string[] {
    if (input.trim().length === 0) {
        return [];
    }

    return input
        .split(">")
        .map((element) =>
            element
                .trim()
                .replace(/\s+/g, " "),
        );
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
import {
    useCallback,
    useEffect,
    useState,
} from "react";

import {
    deleteBrand,
    deleteCategory,
    deleteModel,
    getBrands,
    getCategories,
    getModelsByBrand,
    updateBrand,
    updateCategory,
    updateModel,
} from "../api/dictionariesApi";

import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../types/dictionaries";


function ManageDictionariesPage() {

    const [brands, setBrands] =
        useState<DictionaryBrand[]>([]);

    const [categories, setCategories] =
        useState<DictionaryCategory[]>([]);

    const [models, setModels] =
        useState<DictionaryModel[]>([]);

    const [selectedBrandId, setSelectedBrandId] =
        useState("");

    const [isLoading, setIsLoading] =
        useState(true);

    const [isModelsLoading, setIsModelsLoading] =
        useState(false);

    const [actionKey, setActionKey] =
        useState<string | null>(null);

    const [errorMessage, setErrorMessage] =
        useState<string | null>(null);

    const [successMessage, setSuccessMessage] =
        useState<string | null>(null);


    const loadBaseData =
        useCallback(
            async () => {
                setIsLoading(true);
                setErrorMessage(null);

                try {
                    const [loadedBrands, loadedCategories] =
                        await Promise.all([
                            getBrands(),
                            getCategories(),
                        ]);

                    setBrands(loadedBrands);
                    setCategories(loadedCategories);

                    setSelectedBrandId((current) => {
                        const stillExists =
                            loadedBrands.some(
                                (brand) =>
                                    String(brand.id) === current,
                            );

                        if (stillExists) {
                            return current;
                        }

                        return loadedBrands.length > 0
                            ? String(loadedBrands[0].id)
                            : "";
                    });

                } catch (error) {
                    setErrorMessage(
                        getErrorMessage(
                            error,
                            "Nie udało się pobrać słowników.",
                        ),
                    );
                } finally {
                    setIsLoading(false);
                }
            },
            [],
        );


    const loadModels =
        useCallback(
            async (brandId: number) => {
                setIsModelsLoading(true);

                try {
                    setModels(
                        await getModelsByBrand(
                            brandId,
                        ),
                    );
                } catch (error) {
                    setModels([]);

                    setErrorMessage(
                        getErrorMessage(
                            error,
                            "Nie udało się pobrać modeli.",
                        ),
                    );
                } finally {
                    setIsModelsLoading(false);
                }
            },
            [],
        );


    useEffect(
        () => {
            void loadBaseData();
        },
        [loadBaseData],
    );


    useEffect(
        () => {
            if (selectedBrandId.length === 0) {
                setModels([]);
                return;
            }

            void loadModels(
                Number(selectedBrandId),
            );
        },
        [
            selectedBrandId,
            loadModels,
        ],
    );


    async function runAction(
        key: string,
        action: () => Promise<void>,
        success: string,
    ) {
        if (actionKey !== null) {
            return;
        }

        setActionKey(key);
        setErrorMessage(null);
        setSuccessMessage(null);

        try {
            await action();
            setSuccessMessage(success);
            await loadBaseData();

            if (selectedBrandId.length > 0) {
                await loadModels(
                    Number(selectedBrandId),
                );
            }
        } catch (error) {
            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się wykonać operacji.",
                ),
            );
        } finally {
            setActionKey(null);
        }
    }


    function handleEditBrand(
        brand: DictionaryBrand,
    ) {
        const newName = window.prompt(
            "Nowa nazwa marki:",
            brand.name,
        );

        if (newName === null) {
            return;
        }

        const normalized = normalizeText(newName);

        if (normalized.length === 0
            || normalized === brand.name) {
            return;
        }

        void runAction(
            `brand-edit-${brand.id}`,
            async () => {
                await updateBrand(
                    brand.id,
                    { name: normalized },
                );
            },
            `Zmieniono markę „${brand.name}” na „${normalized}”.`,
        );
    }


    function handleDeleteBrand(
        brand: DictionaryBrand,
    ) {
        if (!window.confirm(
            `Usunąć markę „${brand.name}”? Marka nie może mieć modeli ani być używana przez bota.`,
        )) {
            return;
        }

        void runAction(
            `brand-delete-${brand.id}`,
            async () => {
                await deleteBrand(
                    brand.id,
                );
            },
            `Usunięto markę „${brand.name}”.`,
        );
    }


    function handleEditCategory(
        category: DictionaryCategory,
    ) {
        const newPath = window.prompt(
            "Nowa pełna ścieżka kategorii:",
            category.path,
        );

        if (newPath === null) {
            return;
        }

        const categoryPath =
            parseCategoryPath(
                newPath,
            );

        if (categoryPath.length === 0) {
            return;
        }

        void runAction(
            `category-edit-${category.id}`,
            async () => {
                await updateCategory(
                    category.id,
                    { categoryPath },
                );
            },
            `Zmieniono kategorię „${category.path}”.`,
        );
    }


    function handleDeleteCategory(
        category: DictionaryCategory,
    ) {
        if (!window.confirm(
            `Usunąć kategorię „${category.path}”? Nie może być używana przez żadnego bota.`,
        )) {
            return;
        }

        void runAction(
            `category-delete-${category.id}`,
            async () => {
                await deleteCategory(
                    category.id,
                );
            },
            `Usunięto kategorię „${category.path}”.`,
        );
    }


    function handleEditModel(
        model: DictionaryModel,
    ) {
        const newName = window.prompt(
            "Nowa nazwa modelu:",
            model.name,
        );

        if (newName === null) {
            return;
        }

        const normalized = normalizeText(newName);

        if (normalized.length === 0
            || normalized === model.name) {
            return;
        }

        void runAction(
            `model-edit-${model.id}`,
            async () => {
                await updateModel(
                    model.brandId,
                    model.id,
                    { name: normalized },
                );
            },
            `Zmieniono model „${model.name}” na „${normalized}”.`,
        );
    }


    function handleDeleteModel(
        model: DictionaryModel,
    ) {
        if (!window.confirm(
            `Usunąć model „${model.name}”? Nie może być używany przez żadnego bota.`,
        )) {
            return;
        }

        void runAction(
            `model-delete-${model.id}`,
            async () => {
                await deleteModel(
                    model.brandId,
                    model.id,
                );
            },
            `Usunięto model „${model.name}”.`,
        );
    }


    const isBusy =
        actionKey !== null;


    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">
                        Dane konfiguracyjne
                    </p>

                    <h1 className="page-title">
                        Zarządzaj słownikami
                    </h1>

                    <p className="page-description">
                        Poprawiaj literówki i usuwaj niepotrzebne
                        marki, modele oraz kategorie. Backend blokuje
                        operacje, które mogłyby uszkodzić konfigurację
                        istniejącego bota.
                    </p>
                </div>
            </header>

            {errorMessage !== null && (
                <div className="form-message form-message-error" role="alert">
                    {errorMessage}
                </div>
            )}

            {successMessage !== null && (
                <div className="form-message form-message-success" role="status">
                    {successMessage}
                </div>
            )}

            {isLoading ? (
                <article className="content-card">
                    <div className="dictionary-list-state">
                        Pobieranie słowników...
                    </div>
                </article>
            ) : (
                <div className="dictionary-management-grid">
                    <DictionaryCard
                        title="Marki"
                        emptyMessage="Brak marek."
                        items={brands.map((brand) => ({
                            id: brand.id,
                            title: brand.name,
                            subtitle: `ID: ${brand.id}`,
                            onEdit: () => handleEditBrand(brand),
                            onDelete: () => handleDeleteBrand(brand),
                        }))}
                        disabled={isBusy}
                    />

                    <DictionaryCard
                        title="Kategorie"
                        emptyMessage="Brak kategorii."
                        items={categories.map((category) => ({
                            id: category.id,
                            title: category.name,
                            subtitle: category.path,
                            onEdit: () => handleEditCategory(category),
                            onDelete: () => handleDeleteCategory(category),
                        }))}
                        disabled={isBusy}
                    />

                    <article className="content-card dictionary-model-placeholder">
                        <div className="dictionary-section-header">
                            <div>
                                <h2 className="content-card-title">
                                    Modele
                                </h2>

                                <p className="content-card-text">
                                    Wybierz markę, aby zarządzać jej modelami.
                                </p>
                            </div>
                        </div>

                        <div className="form-field">
                            <label className="form-label" htmlFor="manage-model-brand">
                                Marka
                            </label>

                            <select
                                id="manage-model-brand"
                                className="form-select"
                                value={selectedBrandId}
                                disabled={isBusy || brands.length === 0}
                                onChange={(event) => {
                                    setSelectedBrandId(event.target.value);
                                    setErrorMessage(null);
                                    setSuccessMessage(null);
                                }}
                            >
                                {brands.map((brand) => (
                                    <option key={brand.id} value={brand.id}>
                                        {brand.name}
                                    </option>
                                ))}
                            </select>
                        </div>

                        <div style={{ marginTop: 18 }}>
                            {isModelsLoading ? (
                                <div className="dictionary-list-state">
                                    Pobieranie modeli...
                                </div>
                            ) : models.length === 0 ? (
                                <div className="dictionary-list-state">
                                    Ta marka nie ma modeli.
                                </div>
                            ) : (
                                <ul className="dictionary-list">
                                    {models.map((model) => (
                                        <li key={model.id} className="dictionary-list-item">
                                            <div className="dictionary-item-content">
                                                <div className="dictionary-item-name">
                                                    {model.name}
                                                </div>
                                                <div className="dictionary-item-id">
                                                    ID: {model.id} · {model.brandName}
                                                </div>
                                            </div>

                                            <div style={{ display: "flex", gap: 8 }}>
                                                <button
                                                    className="secondary-button"
                                                    type="button"
                                                    disabled={isBusy}
                                                    onClick={() => handleEditModel(model)}
                                                >
                                                    Edytuj
                                                </button>

                                                <button
                                                    className="bot-stop-button"
                                                    type="button"
                                                    disabled={isBusy}
                                                    onClick={() => handleDeleteModel(model)}
                                                >
                                                    Usuń
                                                </button>
                                            </div>
                                        </li>
                                    ))}
                                </ul>
                            )}
                        </div>
                    </article>
                </div>
            )}
        </section>
    );
}


interface DictionaryCardItem {
    id: number;
    title: string;
    subtitle: string;
    onEdit: () => void;
    onDelete: () => void;
}


interface DictionaryCardProps {
    title: string;
    emptyMessage: string;
    items: DictionaryCardItem[];
    disabled: boolean;
}


function DictionaryCard({
    title,
    emptyMessage,
    items,
    disabled,
}: DictionaryCardProps) {
    return (
        <article className="content-card">
            <div className="dictionary-section-header">
                <div>
                    <h2 className="content-card-title">
                        {title}
                    </h2>
                </div>

                <span className="dictionary-count">
                    {items.length}
                </span>
            </div>

            {items.length === 0 ? (
                <div className="dictionary-list-state">
                    {emptyMessage}
                </div>
            ) : (
                <ul className="dictionary-list">
                    {items.map((item) => (
                        <li key={item.id} className="dictionary-list-item">
                            <div className="dictionary-item-content">
                                <div className="dictionary-item-name">
                                    {item.title}
                                </div>

                                <div className="dictionary-item-path">
                                    {item.subtitle}
                                </div>
                            </div>

                            <div style={{ display: "flex", gap: 8 }}>
                                <button
                                    className="secondary-button"
                                    type="button"
                                    disabled={disabled}
                                    onClick={item.onEdit}
                                >
                                    Edytuj
                                </button>

                                <button
                                    className="bot-stop-button"
                                    type="button"
                                    disabled={disabled}
                                    onClick={item.onDelete}
                                >
                                    Usuń
                                </button>
                            </div>
                        </li>
                    ))}
                </ul>
            )}
        </article>
    );
}


function parseCategoryPath(
    value: string,
): string[] {
    return value
        .split(">")
        .map(normalizeText)
        .filter((element) => element.length > 0);
}


function normalizeText(
    value: string,
): string {
    return value
        .trim()
        .replace(/\s+/g, " ");
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


export default ManageDictionariesPage;

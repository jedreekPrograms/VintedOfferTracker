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

import AppSelect, {
    type AppSelectOption,
} from "../components/AppSelect";

import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../types/dictionaries";

import type {
    TargetMode,
} from "../types/bots";

function DictionariesPage() {
    const [brands, setBrands] = useState<DictionaryBrand[]>([]);
    const [categories, setCategories] = useState<DictionaryCategory[]>([]);
    const [models, setModels] = useState<DictionaryModel[]>([]);

    const [brandName, setBrandName] = useState("");
    const [categoryPathInput, setCategoryPathInput] = useState("");
    const [selectedBrandId, setSelectedBrandId] = useState("");
    const [selectedModelCategoryId, setSelectedModelCategoryId] = useState("");
    const [modelName, setModelName] = useState("");
    const [modelTargetMode, setModelTargetMode] = useState<TargetMode>("VINTED_MODEL");

    const [isLoading, setIsLoading] = useState(true);
    const [areModelsLoading, setAreModelsLoading] = useState(false);
    const [submitting, setSubmitting] = useState<string | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);

    const loadBaseData = useCallback(async () => {
        setIsLoading(true);
        setErrorMessage(null);

        try {
            const [loadedBrands, loadedCategories] = await Promise.all([
                getBrands(),
                getCategories(),
            ]);

            const sortedBrands = [...loadedBrands].sort((left, right) =>
                left.name.localeCompare(right.name, "pl"),
            );
            const sortedCategories = [...loadedCategories].sort((left, right) =>
                left.path.localeCompare(right.path, "pl"),
            );

            setBrands(sortedBrands);
            setCategories(sortedCategories);

            setSelectedBrandId(current => {
                if (sortedBrands.some(brand => String(brand.id) === current)) {
                    return current;
                }

                return sortedBrands.length > 0
                    ? String(sortedBrands[0].id)
                    : "";
            });

            setSelectedModelCategoryId(current => {
                if (sortedCategories.some(category => String(category.id) === current)) {
                    return current;
                }

                return sortedCategories.length > 0
                    ? String(sortedCategories[0].id)
                    : "";
            });
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Nie udało się pobrać słowników."));
        } finally {
            setIsLoading(false);
        }
    }, []);

    const loadModels = useCallback(async (brandId: number) => {
        setAreModelsLoading(true);

        try {
            const loadedModels = await getModelsByBrand(brandId);
            setModels(
                [...loadedModels].sort((left, right) =>
                    left.name.localeCompare(right.name, "pl"),
                ),
            );
        } catch (error) {
            setModels([]);
            setErrorMessage(getErrorMessage(error, "Nie udało się pobrać modeli."));
        } finally {
            setAreModelsLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadBaseData();
    }, [loadBaseData]);

    useEffect(() => {
        if (selectedBrandId.length === 0) {
            setModels([]);
            return;
        }

        void loadModels(Number(selectedBrandId));
    }, [selectedBrandId, loadModels]);

    function clearMessages() {
        setErrorMessage(null);
        setSuccessMessage(null);
    }

    async function handleCreateBrand(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const name = normalizeText(brandName);

        if (name.length === 0) {
            setErrorMessage("Wpisz nazwę marki.");
            return;
        }

        setSubmitting("brand");
        clearMessages();

        try {
            const created = await createBrand({ name });
            setBrandName("");
            setSuccessMessage(`Dodano markę ${created.name}.`);
            await loadBaseData();
            setSelectedBrandId(String(created.id));
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Nie udało się dodać marki."));
        } finally {
            setSubmitting(null);
        }
    }

    async function handleCreateCategory(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const categoryPath = categoryPathInput
            .split(">")
            .map(normalizeText)
            .filter(element => element.length > 0);

        if (categoryPath.length === 0) {
            setErrorMessage("Wpisz pełną ścieżkę kategorii.");
            return;
        }

        setSubmitting("category");
        clearMessages();

        try {
            const created = await createCategory({ categoryPath });
            setCategoryPathInput("");
            setSuccessMessage(`Dodano kategorię ${created.path}.`);
            await loadBaseData();
            setSelectedModelCategoryId(String(created.id));
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Nie udało się dodać kategorii."));
        } finally {
            setSubmitting(null);
        }
    }

    async function handleCreateModel(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const name = normalizeText(modelName);
        const brandId = Number(selectedBrandId);
        const categoryId = Number(selectedModelCategoryId);

        if (!Number.isInteger(brandId) || brandId <= 0) {
            setErrorMessage("Najpierw wybierz markę.");
            return;
        }

        if (!Number.isInteger(categoryId) || categoryId <= 0) {
            setErrorMessage("Najpierw wybierz kategorię modelu.");
            return;
        }

        if (name.length === 0) {
            setErrorMessage("Wpisz nazwę modelu.");
            return;
        }

        setSubmitting("model");
        clearMessages();

        try {
            const created = await createModel(brandId, {
                name,
                targetMode: modelTargetMode,
                categoryId,
            });

            setModelName("");
            setModelTargetMode("VINTED_MODEL");
            setSuccessMessage(
                `Dodano ${created.brandName} ${created.name} · ${created.categoryPath ?? "bez kategorii"} · ${targetModeLabel(created.targetMode)}.`,
            );
            await loadModels(brandId);
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Nie udało się dodać modelu."));
        } finally {
            setSubmitting(null);
        }
    }

    const selectedBrand = brands.find(
        brand => String(brand.id) === selectedBrandId,
    ) ?? null;

    const categoryOptions: AppSelectOption[] = categories.length === 0
        ? [{ value: "", label: "Najpierw dodaj kategorię", disabled: true }]
        : categories.map(category => ({
            value: String(category.id),
            label: category.path,
        }));

    const brandOptions: AppSelectOption[] = brands.length === 0
        ? [{ value: "", label: "Najpierw dodaj markę", disabled: true }]
        : brands.map(brand => ({
            value: String(brand.id),
            label: brand.name,
        }));

    const targetModeOptions: AppSelectOption[] = [
        {
            value: "VINTED_MODEL",
            label: "Model z listy Vinted",
        },
        {
            value: "SEARCH_QUERY",
            label: "Wyszukiwanie tekstowe",
        },
    ];

    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">Dane konfiguracyjne</p>
                    <h1 className="page-title">Słowniki</h1>
                    <p className="page-description">
                        Dodawaj marki, kategorie i modele. Kategoria oraz sposób wyszukiwania
                        są zapisane bezpośrednio przy modelu, dzięki czemu observer i zwykłe
                        boty używają tej samej definicji produktu.
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
                    <div className="dictionary-list-state">Pobieranie słowników...</div>
                </article>
            ) : (
                <div className="dictionary-management-grid">
                    <article className="content-card">
                        <div className="dictionary-section-header">
                            <div>
                                <h2 className="content-card-title">Dodaj markę</h2>
                                <p className="content-card-text">Np. Apple, Samsung, Xiaomi.</p>
                            </div>
                            <span className="dictionary-count">{brands.length}</span>
                        </div>

                        <form className="dictionary-form" onSubmit={handleCreateBrand}>
                            <div className="form-field">
                                <label className="form-label" htmlFor="brand-name">Nazwa marki</label>
                                <input
                                    id="brand-name"
                                    className="form-input"
                                    value={brandName}
                                    maxLength={255}
                                    placeholder="np. Apple"
                                    onChange={event => {
                                        setBrandName(event.target.value);
                                        clearMessages();
                                    }}
                                />
                            </div>
                            <button className="primary-button" disabled={submitting !== null}>
                                {submitting === "brand" ? "Dodawanie..." : "Dodaj markę"}
                            </button>
                        </form>
                    </article>

                    <DictionaryListCard
                        title="Zapisane marki"
                        items={brands.map(brand => brand.name)}
                        emptyMessage="Brak marek."
                    />

                    <article className="content-card">
                        <div className="dictionary-section-header">
                            <div>
                                <h2 className="content-card-title">Dodaj kategorię</h2>
                                <p className="content-card-text">Wpisz pełną ścieżkę kategorii Vinted.</p>
                            </div>
                            <span className="dictionary-count">{categories.length}</span>
                        </div>

                        <form className="dictionary-form" onSubmit={handleCreateCategory}>
                            <div className="form-field">
                                <label className="form-label" htmlFor="category-path">Ścieżka</label>
                                <input
                                    id="category-path"
                                    className="form-input"
                                    value={categoryPathInput}
                                    placeholder="Elektronika > Telefony komórkowe > Smartfony"
                                    onChange={event => {
                                        setCategoryPathInput(event.target.value);
                                        clearMessages();
                                    }}
                                />
                            </div>
                            <button className="primary-button" disabled={submitting !== null}>
                                {submitting === "category" ? "Dodawanie..." : "Dodaj kategorię"}
                            </button>
                        </form>
                    </article>

                    <DictionaryListCard
                        title="Zapisane kategorie"
                        items={categories.map(category => category.path)}
                        emptyMessage="Brak kategorii."
                    />

                    <article className="content-card dictionary-model-create-card">
                        <div className="dictionary-section-header">
                            <div>
                                <h2 className="content-card-title">Dodaj model</h2>
                                <p className="content-card-text">
                                    Wybierz kategorię i markę, wpisz nazwę modelu, a na końcu określ
                                    sposób wyszukiwania. Formularz zachowuje tę samą kolejność również
                                    na mniejszych ekranach.
                                </p>
                            </div>
                            <span className="dictionary-count">{models.length}</span>
                        </div>

                        <form
                            className="dictionary-model-create-form"
                            onSubmit={handleCreateModel}
                        >
                            <div className="form-field">
                                <label className="form-label" htmlFor="model-category">Kategoria</label>
                                <AppSelect
                                    id="model-category"
                                    value={selectedModelCategoryId}
                                    options={categoryOptions}
                                    ariaLabel="Kategoria modelu"
                                    disabled={categories.length === 0 || submitting !== null}
                                    onChange={value => {
                                        setSelectedModelCategoryId(value);
                                        clearMessages();
                                    }}
                                />
                            </div>

                            <div className="form-field">
                                <label className="form-label" htmlFor="model-brand">Marka</label>
                                <AppSelect
                                    id="model-brand"
                                    value={selectedBrandId}
                                    options={brandOptions}
                                    ariaLabel="Marka modelu"
                                    disabled={brands.length === 0 || submitting !== null}
                                    onChange={value => {
                                        setSelectedBrandId(value);
                                        setModelName("");
                                        clearMessages();
                                    }}
                                />
                            </div>

                            <div className="form-field">
                                <label className="form-label" htmlFor="model-name">Nazwa modelu</label>
                                <input
                                    id="model-name"
                                    className="form-input"
                                    value={modelName}
                                    maxLength={255}
                                    placeholder="np. Galaxy Tab S11 Ultra"
                                    onChange={event => {
                                        setModelName(event.target.value);
                                        clearMessages();
                                    }}
                                />
                            </div>

                            <div className="form-field dictionary-model-mode-field">
                                <label className="form-label" htmlFor="model-target-mode">
                                    Sposób wyszukiwania
                                </label>
                                <AppSelect
                                    id="model-target-mode"
                                    value={modelTargetMode}
                                    options={targetModeOptions}
                                    ariaLabel="Sposób wyszukiwania modelu"
                                    disabled={submitting !== null}
                                    onChange={value => {
                                        setModelTargetMode(value as TargetMode);
                                        clearMessages();
                                    }}
                                />
                                <span className="form-help">
                                    Filtr Vinted wybiera kategorię, markę i model. Wyszukiwarka wpisuje
                                    nazwę modelu, a następnie ustawia kategorię i markę.
                                </span>
                            </div>

                            <div className="dictionary-model-create-actions">
                                <button
                                    className="primary-button"
                                    disabled={submitting !== null || categories.length === 0 || brands.length === 0}
                                >
                                    {submitting === "model" ? "Dodawanie..." : "Dodaj model"}
                                </button>
                            </div>
                        </form>
                    </article>

                    <article className="content-card">
                        <div className="dictionary-section-header">
                            <div>
                                <h2 className="content-card-title">Zapisane modele</h2>
                                <p className="content-card-text">
                                    {selectedBrand === null
                                        ? "Wybierz markę."
                                        : `Modele marki ${selectedBrand.name}.`}
                                </p>
                            </div>
                        </div>

                        {areModelsLoading ? (
                            <div className="dictionary-list-state">Pobieranie modeli...</div>
                        ) : models.length === 0 ? (
                            <div className="dictionary-list-state">Brak modeli dla tej marki.</div>
                        ) : (
                            <ul className="dictionary-list">
                                {models.map(model => (
                                    <li key={model.id} className="dictionary-list-item">
                                        <div className="dictionary-item-content">
                                            <div className="dictionary-item-name">{model.name}</div>
                                            <div className="dictionary-item-path">
                                                {model.categoryPath ?? "Brak przypisanej kategorii"}
                                            </div>
                                            <div className="dictionary-item-id">
                                                ID: {model.id} · {model.brandName}
                                            </div>
                                        </div>
                                        <span className="dictionary-item-type">
                                            {model.targetMode === "SEARCH_QUERY" ? "Wyszukiwarka" : "Filtr Vinted"}
                                        </span>
                                    </li>
                                ))}
                            </ul>
                        )}
                    </article>
                </div>
            )}
        </section>
    );
}

interface DictionaryListCardProps {
    title: string;
    items: string[];
    emptyMessage: string;
}

function DictionaryListCard({ title, items, emptyMessage }: DictionaryListCardProps) {
    return (
        <article className="content-card">
            <div className="dictionary-section-header">
                <h2 className="content-card-title">{title}</h2>
                <span className="dictionary-count">{items.length}</span>
            </div>

            {items.length === 0 ? (
                <div className="dictionary-list-state">{emptyMessage}</div>
            ) : (
                <ul className="dictionary-list">
                    {items.map(item => (
                        <li key={item} className="dictionary-list-item">
                            <div className="dictionary-item-name">{item}</div>
                        </li>
                    ))}
                </ul>
            )}
        </article>
    );
}

function targetModeLabel(targetMode: TargetMode): string {
    return targetMode === "SEARCH_QUERY"
        ? "wyszukiwanie tekstowe"
        : "filtr Vinted";
}

function normalizeText(value: string): string {
    return value.trim().replace(/\s+/g, " ");
}

function getErrorMessage(error: unknown, fallback: string): string {
    return error instanceof Error
        ? error.message
        : fallback;
}

export default DictionariesPage;

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


type EditingEntry = {
    id: number;
    value: string;
} | null;


function DictionaryManagementPage() {

    const [brands, setBrands] =
        useState<DictionaryBrand[]>([]);

    const [categories, setCategories] =
        useState<DictionaryCategory[]>([]);

    const [models, setModels] =
        useState<DictionaryModel[]>([]);

    const [selectedBrandId, setSelectedBrandId] =
        useState("");

    const [editingBrand, setEditingBrand] =
        useState<EditingEntry>(null);

    const [editingCategory, setEditingCategory] =
        useState<EditingEntry>(null);

    const [editingModel, setEditingModel] =
        useState<EditingEntry>(null);

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


    const loadBaseDictionaries =
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

                    setBrands(
                        [...loadedBrands].sort(compareBrands),
                    );

                    setCategories(
                        [...loadedCategories].sort(compareCategories),
                    );

                    setSelectedBrandId(
                        (currentBrandId) => {

                            if (
                                loadedBrands.some(
                                    (brand) =>
                                        String(brand.id)
                                        === currentBrandId,
                                )
                            ) {

                                return currentBrandId;
                            }

                            return loadedBrands.length > 0
                                ? String(loadedBrands[0].id)
                                : "";
                        },
                    );

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

                    const loadedModels =
                        await getModelsByBrand(
                            brandId,
                        );

                    setModels(
                        [...loadedModels].sort(compareModels),
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
            void loadBaseDictionaries();
        },
        [loadBaseDictionaries],
    );


    useEffect(
        () => {

            setEditingModel(null);

            if (
                selectedBrandId.length === 0
            ) {

                setModels([]);
                return;
            }

            void loadModels(
                Number(selectedBrandId),
            );

        },
        [selectedBrandId, loadModels],
    );


    function clearMessages() {
        setErrorMessage(null);
        setSuccessMessage(null);
    }


    async function handleSaveBrand() {

        if (
            editingBrand === null
            || actionKey !== null
        ) {
            return;
        }

        const name = normalizeText(editingBrand.value);

        if (name.length === 0) {
            setErrorMessage("Nazwa marki nie może być pusta.");
            return;
        }

        const key = `brand-${editingBrand.id}`;
        setActionKey(key);
        clearMessages();

        try {

            const updated = await updateBrand(
                editingBrand.id,
                { name },
            );

            setBrands((current) =>
                current
                    .map((brand) =>
                        brand.id === updated.id
                            ? updated
                            : brand,
                    )
                    .sort(compareBrands),
            );

            setEditingBrand(null);
            setSuccessMessage(`Zmieniono markę na: ${updated.name}.`);

        } catch (error) {

            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się zmienić marki.",
                ),
            );

        } finally {
            setActionKey(null);
        }
    }


    async function handleDeleteBrand(
        brand: DictionaryBrand,
    ) {

        if (actionKey !== null) {
            return;
        }

        if (
            !window.confirm(
                `Usunąć markę „${brand.name}”? Marka nie może być używana przez żadnego bota ani mieć zapisanych modeli.`,
            )
        ) {
            return;
        }

        const key = `brand-${brand.id}`;
        setActionKey(key);
        clearMessages();

        try {

            await deleteBrand(brand.id);

            setBrands((current) =>
                current.filter((item) => item.id !== brand.id),
            );

            setSuccessMessage(`Usunięto markę: ${brand.name}.`);

        } catch (error) {

            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się usunąć marki.",
                ),
            );

        } finally {
            setActionKey(null);
        }
    }


    async function handleSaveCategory() {

        if (
            editingCategory === null
            || actionKey !== null
        ) {
            return;
        }

        const categoryPath = parseCategoryPath(
            editingCategory.value,
        );

        if (
            categoryPath.length === 0
            || categoryPath.some((element) => element.length === 0)
        ) {
            setErrorMessage("Ścieżka kategorii jest nieprawidłowa.");
            return;
        }

        const key = `category-${editingCategory.id}`;
        setActionKey(key);
        clearMessages();

        try {

            const updated = await updateCategory(
                editingCategory.id,
                { categoryPath },
            );

            setCategories((current) =>
                current
                    .map((category) =>
                        category.id === updated.id
                            ? updated
                            : category,
                    )
                    .sort(compareCategories),
            );

            setEditingCategory(null);
            setSuccessMessage(`Zmieniono kategorię na: ${updated.path}.`);

        } catch (error) {

            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się zmienić kategorii.",
                ),
            );

        } finally {
            setActionKey(null);
        }
    }


    async function handleDeleteCategory(
        category: DictionaryCategory,
    ) {

        if (actionKey !== null) {
            return;
        }

        if (
            !window.confirm(
                `Usunąć kategorię „${category.path}”? Nie można usunąć kategorii używanej przez konfigurację bota.`,
            )
        ) {
            return;
        }

        const key = `category-${category.id}`;
        setActionKey(key);
        clearMessages();

        try {

            await deleteCategory(category.id);

            setCategories((current) =>
                current.filter((item) => item.id !== category.id),
            );

            setSuccessMessage(`Usunięto kategorię: ${category.path}.`);

        } catch (error) {

            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się usunąć kategorii.",
                ),
            );

        } finally {
            setActionKey(null);
        }
    }


    async function handleSaveModel() {

        if (
            editingModel === null
            || selectedBrandId.length === 0
            || actionKey !== null
        ) {
            return;
        }

        const name = normalizeText(editingModel.value);

        if (name.length === 0) {
            setErrorMessage("Nazwa modelu nie może być pusta.");
            return;
        }

        const brandId = Number(selectedBrandId);
        const key = `model-${editingModel.id}`;
        setActionKey(key);
        clearMessages();

        try {

            const updated = await updateModel(
                brandId,
                editingModel.id,
                { name },
            );

            setModels((current) =>
                current
                    .map((model) =>
                        model.id === updated.id
                            ? updated
                            : model,
                    )
                    .sort(compareModels),
            );

            setEditingModel(null);
            setSuccessMessage(`Zmieniono model na: ${updated.name}.`);

        } catch (error) {

            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się zmienić modelu.",
                ),
            );

        } finally {
            setActionKey(null);
        }
    }


    async function handleDeleteModel(
        model: DictionaryModel,
    ) {

        if (
            actionKey !== null
            || selectedBrandId.length === 0
        ) {
            return;
        }

        if (
            !window.confirm(
                `Usunąć model „${model.name}”? Nie można usunąć modelu używanego przez konfigurację bota.`,
            )
        ) {
            return;
        }

        const brandId = Number(selectedBrandId);
        const key = `model-${model.id}`;
        setActionKey(key);
        clearMessages();

        try {

            await deleteModel(
                brandId,
                model.id,
            );

            setModels((current) =>
                current.filter((item) => item.id !== model.id),
            );

            setSuccessMessage(`Usunięto model: ${model.name}.`);

        } catch (error) {

            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się usunąć modelu.",
                ),
            );

        } finally {
            setActionKey(null);
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
                        Edycja słowników
                    </h1>

                    <p className="page-description">
                        Poprawiaj literówki i usuwaj niepotrzebne marki,
                        modele oraz kategorie. Backend blokuje operacje,
                        które mogłyby uszkodzić aktywną konfigurację bota.
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
                    <DictionaryBrandCard
                        brands={brands}
                        editingBrand={editingBrand}
                        actionKey={actionKey}
                        onEdit={setEditingBrand}
                        onSave={() => void handleSaveBrand()}
                        onCancel={() => setEditingBrand(null)}
                        onDelete={(brand) => void handleDeleteBrand(brand)}
                    />

                    <DictionaryCategoryCard
                        categories={categories}
                        editingCategory={editingCategory}
                        actionKey={actionKey}
                        onEdit={setEditingCategory}
                        onSave={() => void handleSaveCategory()}
                        onCancel={() => setEditingCategory(null)}
                        onDelete={(category) => void handleDeleteCategory(category)}
                    />

                    <DictionaryModelCard
                        brands={brands}
                        models={models}
                        selectedBrandId={selectedBrandId}
                        editingModel={editingModel}
                        actionKey={actionKey}
                        isModelsLoading={isModelsLoading}
                        onBrandChange={setSelectedBrandId}
                        onEdit={setEditingModel}
                        onSave={() => void handleSaveModel()}
                        onCancel={() => setEditingModel(null)}
                        onDelete={(model) => void handleDeleteModel(model)}
                    />
                </div>
            )}
        </section>
    );
}


interface BrandCardProps {
    brands: DictionaryBrand[];
    editingBrand: EditingEntry;
    actionKey: string | null;
    onEdit: (value: EditingEntry) => void;
    onSave: () => void;
    onCancel: () => void;
    onDelete: (brand: DictionaryBrand) => void;
}


function DictionaryBrandCard({
    brands,
    editingBrand,
    actionKey,
    onEdit,
    onSave,
    onCancel,
    onDelete,
}: BrandCardProps) {
    return (
        <article className="content-card">
            <h2 className="content-card-title">Marki</h2>
            <p className="content-card-text">
                Zmiana nazwy aktualizuje również bezpieczne konfiguracje botów.
            </p>

            {brands.length === 0 ? (
                <div className="dictionary-list-state">Brak marek.</div>
            ) : (
                <ul className="dictionary-list">
                    {brands.map((brand) => {
                        const editing = editingBrand?.id === brand.id;
                        const busy = actionKey === `brand-${brand.id}`;

                        return (
                            <li key={brand.id} className="dictionary-list-item">
                                <div className="dictionary-item-content">
                                    {editing ? (
                                        <input
                                            className="form-input"
                                            value={editingBrand.value}
                                            onChange={(event) =>
                                                onEdit({
                                                    id: brand.id,
                                                    value: event.target.value,
                                                })
                                            }
                                        />
                                    ) : (
                                        <div className="dictionary-item-name">{brand.name}</div>
                                    )}
                                    <div className="dictionary-item-id">ID: {brand.id}</div>
                                </div>

                                <div className="bot-row-actions">
                                    {editing ? (
                                        <>
                                            <button className="primary-button" type="button" disabled={busy} onClick={onSave}>Zapisz</button>
                                            <button className="secondary-button" type="button" disabled={busy} onClick={onCancel}>Anuluj</button>
                                        </>
                                    ) : (
                                        <>
                                            <button className="secondary-button" type="button" disabled={actionKey !== null} onClick={() => onEdit({ id: brand.id, value: brand.name })}>Edytuj</button>
                                            <button className="bot-stop-button" type="button" disabled={actionKey !== null} onClick={() => onDelete(brand)}>Usuń</button>
                                        </>
                                    )}
                                </div>
                            </li>
                        );
                    })}
                </ul>
            )}
        </article>
    );
}


interface CategoryCardProps {
    categories: DictionaryCategory[];
    editingCategory: EditingEntry;
    actionKey: string | null;
    onEdit: (value: EditingEntry) => void;
    onSave: () => void;
    onCancel: () => void;
    onDelete: (category: DictionaryCategory) => void;
}


function DictionaryCategoryCard({
    categories,
    editingCategory,
    actionKey,
    onEdit,
    onSave,
    onCancel,
    onDelete,
}: CategoryCardProps) {
    return (
        <article className="content-card">
            <h2 className="content-card-title">Kategorie</h2>
            <p className="content-card-text">
                Edytuj pełną ścieżkę, np. Elektronika &gt; Telefony &gt; Smartfony.
            </p>

            {categories.length === 0 ? (
                <div className="dictionary-list-state">Brak kategorii.</div>
            ) : (
                <ul className="dictionary-list">
                    {categories.map((category) => {
                        const editing = editingCategory?.id === category.id;
                        const busy = actionKey === `category-${category.id}`;

                        return (
                            <li key={category.id} className="dictionary-list-item">
                                <div className="dictionary-item-content">
                                    {editing ? (
                                        <input
                                            className="form-input"
                                            value={editingCategory.value}
                                            onChange={(event) =>
                                                onEdit({
                                                    id: category.id,
                                                    value: event.target.value,
                                                })
                                            }
                                        />
                                    ) : (
                                        <>
                                            <div className="dictionary-item-name">{category.name}</div>
                                            <div className="dictionary-item-path">{category.path}</div>
                                        </>
                                    )}
                                    <div className="dictionary-item-id">ID: {category.id}</div>
                                </div>

                                <div className="bot-row-actions">
                                    {editing ? (
                                        <>
                                            <button className="primary-button" type="button" disabled={busy} onClick={onSave}>Zapisz</button>
                                            <button className="secondary-button" type="button" disabled={busy} onClick={onCancel}>Anuluj</button>
                                        </>
                                    ) : (
                                        <>
                                            <button className="secondary-button" type="button" disabled={actionKey !== null} onClick={() => onEdit({ id: category.id, value: category.path })}>Edytuj</button>
                                            <button className="bot-stop-button" type="button" disabled={actionKey !== null} onClick={() => onDelete(category)}>Usuń</button>
                                        </>
                                    )}
                                </div>
                            </li>
                        );
                    })}
                </ul>
            )}
        </article>
    );
}


interface ModelCardProps {
    brands: DictionaryBrand[];
    models: DictionaryModel[];
    selectedBrandId: string;
    editingModel: EditingEntry;
    actionKey: string | null;
    isModelsLoading: boolean;
    onBrandChange: (value: string) => void;
    onEdit: (value: EditingEntry) => void;
    onSave: () => void;
    onCancel: () => void;
    onDelete: (model: DictionaryModel) => void;
}


function DictionaryModelCard({
    brands,
    models,
    selectedBrandId,
    editingModel,
    actionKey,
    isModelsLoading,
    onBrandChange,
    onEdit,
    onSave,
    onCancel,
    onDelete,
}: ModelCardProps) {
    return (
        <article className="content-card dictionary-model-placeholder">
            <div className="dictionary-section-header">
                <div>
                    <h2 className="content-card-title">Modele</h2>
                    <p className="content-card-text">Najpierw wybierz markę.</p>
                </div>

                <select
                    className="form-select"
                    value={selectedBrandId}
                    disabled={brands.length === 0 || actionKey !== null}
                    onChange={(event) => onBrandChange(event.target.value)}
                >
                    {brands.map((brand) => (
                        <option key={brand.id} value={brand.id}>
                            {brand.name}
                        </option>
                    ))}
                </select>
            </div>

            {isModelsLoading ? (
                <div className="dictionary-list-state">Pobieranie modeli...</div>
            ) : models.length === 0 ? (
                <div className="dictionary-list-state">Brak modeli dla wybranej marki.</div>
            ) : (
                <ul className="dictionary-list">
                    {models.map((model) => {
                        const editing = editingModel?.id === model.id;
                        const busy = actionKey === `model-${model.id}`;

                        return (
                            <li key={model.id} className="dictionary-list-item">
                                <div className="dictionary-item-content">
                                    {editing ? (
                                        <input
                                            className="form-input"
                                            value={editingModel.value}
                                            onChange={(event) =>
                                                onEdit({
                                                    id: model.id,
                                                    value: event.target.value,
                                                })
                                            }
                                        />
                                    ) : (
                                        <>
                                            <div className="dictionary-item-name">{model.name}</div>
                                            <div className="dictionary-item-path">Marka: {model.brandName}</div>
                                        </>
                                    )}
                                    <div className="dictionary-item-id">ID: {model.id}</div>
                                </div>

                                <div className="bot-row-actions">
                                    {editing ? (
                                        <>
                                            <button className="primary-button" type="button" disabled={busy} onClick={onSave}>Zapisz</button>
                                            <button className="secondary-button" type="button" disabled={busy} onClick={onCancel}>Anuluj</button>
                                        </>
                                    ) : (
                                        <>
                                            <button className="secondary-button" type="button" disabled={actionKey !== null} onClick={() => onEdit({ id: model.id, value: model.name })}>Edytuj</button>
                                            <button className="bot-stop-button" type="button" disabled={actionKey !== null} onClick={() => onDelete(model)}>Usuń</button>
                                        </>
                                    )}
                                </div>
                            </li>
                        );
                    })}
                </ul>
            )}
        </article>
    );
}


function normalizeText(value: string): string {
    return value.trim().replace(/\s+/g, " ");
}


function parseCategoryPath(input: string): string[] {
    if (input.trim().length === 0) {
        return [];
    }

    return input.split(">").map(normalizeText);
}


function compareBrands(first: DictionaryBrand, second: DictionaryBrand): number {
    return first.name.localeCompare(second.name, "pl");
}


function compareCategories(first: DictionaryCategory, second: DictionaryCategory): number {
    return first.path.localeCompare(second.path, "pl");
}


function compareModels(first: DictionaryModel, second: DictionaryModel): number {
    return first.name.localeCompare(second.name, "pl");
}


function getErrorMessage(error: unknown, fallbackMessage: string): string {
    if (error instanceof Error) {
        return error.message;
    }

    return fallbackMessage;
}


export default DictionaryManagementPage;

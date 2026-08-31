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
    updateModelCategory,
} from "../api/dictionariesApi";
import AppDialog from "../components/AppDialog";
import AppSelect, { type AppSelectOption } from "../components/AppSelect";
import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../types/dictionaries";

type EditTarget =
    | { kind: "BRAND"; item: DictionaryBrand }
    | { kind: "CATEGORY"; item: DictionaryCategory }
    | { kind: "MODEL"; item: DictionaryModel };

type DeleteTarget = EditTarget;

function ManageDictionariesPage() {
    const [brands, setBrands] = useState<DictionaryBrand[]>([]);
    const [categories, setCategories] = useState<DictionaryCategory[]>([]);
    const [models, setModels] = useState<DictionaryModel[]>([]);
    const [selectedBrandId, setSelectedBrandId] = useState("");
    const [isLoading, setIsLoading] = useState(true);
    const [isModelsLoading, setIsModelsLoading] = useState(false);
    const [actionKey, setActionKey] = useState<string | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);
    const [editTarget, setEditTarget] = useState<EditTarget | null>(null);
    const [editValue, setEditValue] = useState("");
    const [editError, setEditError] = useState<string | null>(null);
    const [deleteTarget, setDeleteTarget] = useState<DeleteTarget | null>(null);

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
            setBrands(sortedBrands);
            setCategories(
                [...loadedCategories].sort((left, right) =>
                    left.path.localeCompare(right.path, "pl"),
                ),
            );
            setSelectedBrandId((current) => {
                if (sortedBrands.some((brand) => String(brand.id) === current)) {
                    return current;
                }
                return sortedBrands.length > 0 ? String(sortedBrands[0].id) : "";
            });
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Nie udało się pobrać słowników."));
        } finally {
            setIsLoading(false);
        }
    }, []);

    const loadModels = useCallback(async (brandId: number) => {
        setIsModelsLoading(true);
        try {
            setModels(
                [...await getModelsByBrand(brandId)].sort((left, right) =>
                    left.name.localeCompare(right.name, "pl"),
                ),
            );
        } catch (error) {
            setModels([]);
            setErrorMessage(getErrorMessage(error, "Nie udało się pobrać modeli."));
        } finally {
            setIsModelsLoading(false);
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
                await loadModels(Number(selectedBrandId));
            }
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Nie udało się wykonać operacji."));
        } finally {
            setActionKey(null);
        }
    }

    function openEditDialog(target: EditTarget) {
        setEditTarget(target);
        setEditError(null);
        switch (target.kind) {
            case "BRAND":
            case "MODEL":
                setEditValue(target.item.name);
                break;
            case "CATEGORY":
                setEditValue(target.item.path);
                break;
        }
    }

    function closeEditDialog() {
        if (actionKey === null) {
            setEditTarget(null);
            setEditValue("");
            setEditError(null);
        }
    }

    async function confirmEdit() {
        if (editTarget === null || actionKey !== null) {
            return;
        }

        const target = editTarget;
        if (target.kind === "CATEGORY") {
            const categoryPath = parseCategoryPath(editValue);
            if (categoryPath.length === 0) {
                setEditError("Wpisz prawidłową, niepustą ścieżkę kategorii.");
                return;
            }
            if (categoryPath.join(" > ") === target.item.path) {
                closeEditDialog();
                return;
            }

            setEditTarget(null);
            await runAction(
                `category-edit-${target.item.id}`,
                async () => updateCategory(target.item.id, { categoryPath }),
                `Zmieniono kategorię „${target.item.path}”.`,
            );
            return;
        }

        const normalized = normalizeText(editValue);
        if (normalized.length === 0) {
            setEditError("Nazwa nie może być pusta.");
            return;
        }
        if (normalized === target.item.name) {
            closeEditDialog();
            return;
        }

        setEditTarget(null);
        if (target.kind === "BRAND") {
            await runAction(
                `brand-edit-${target.item.id}`,
                async () => updateBrand(target.item.id, { name: normalized }),
                `Zmieniono markę „${target.item.name}” na „${normalized}”.`,
            );
        } else {
            await runAction(
                `model-edit-${target.item.id}`,
                async () => updateModel(
                    target.item.brandId,
                    target.item.id,
                    { name: normalized },
                ),
                `Zmieniono model „${target.item.name}” na „${normalized}”.`,
            );
        }
    }

    async function confirmDelete() {
        if (deleteTarget === null || actionKey !== null) {
            return;
        }

        const target = deleteTarget;
        setDeleteTarget(null);
        switch (target.kind) {
            case "BRAND":
                await runAction(
                    `brand-delete-${target.item.id}`,
                    async () => deleteBrand(target.item.id),
                    `Usunięto markę „${target.item.name}”.`,
                );
                break;
            case "CATEGORY":
                await runAction(
                    `category-delete-${target.item.id}`,
                    async () => deleteCategory(target.item.id),
                    `Usunięto kategorię „${target.item.path}”.`,
                );
                break;
            case "MODEL":
                await runAction(
                    `model-delete-${target.item.id}`,
                    async () => deleteModel(target.item.brandId, target.item.id),
                    `Usunięto model „${target.item.name}”.`,
                );
                break;
        }
    }

    function handleModelCategoryChange(model: DictionaryModel, rawCategoryId: string) {
        const categoryId = Number(rawCategoryId);
        if (!Number.isInteger(categoryId) || categoryId <= 0 || categoryId === model.categoryId) {
            return;
        }

        const category = categories.find((item) => item.id === categoryId);
        if (category === undefined) {
            return;
        }

        void runAction(
            `model-category-${model.id}`,
            async () => updateModelCategory(
                model.brandId,
                model.id,
                { categoryId },
            ),
            `Przypisano model „${model.name}” do kategorii „${category.path}”.`,
        );
    }

    const isBusy = actionKey !== null;
    const brandOptions: AppSelectOption[] = brands.map((brand) => ({
        value: String(brand.id),
        label: brand.name,
    }));
    const categoryOptions: AppSelectOption[] = [
        { value: "", label: "Przypisz kategorię", disabled: true },
        ...categories.map((category) => ({
            value: String(category.id),
            label: category.path,
        })),
    ];

    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">Dane konfiguracyjne</p>
                    <h1 className="page-title">Zarządzaj słownikami</h1>
                    <p className="page-description">
                        Poprawiaj literówki, przypisuj kategorie do modeli i usuwaj
                        niepotrzebne wpisy. Wszystkie potwierdzenia i edycje otwierają się
                        teraz bezpośrednio w interfejsie aplikacji.
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
                    <DictionaryCard
                        title="Marki"
                        emptyMessage="Brak marek."
                        items={brands.map((brand) => ({
                            id: brand.id,
                            title: brand.name,
                            subtitle: `ID: ${brand.id}`,
                            onEdit: () => openEditDialog({ kind: "BRAND", item: brand }),
                            onDelete: () => setDeleteTarget({ kind: "BRAND", item: brand }),
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
                            onEdit: () => openEditDialog({ kind: "CATEGORY", item: category }),
                            onDelete: () => setDeleteTarget({ kind: "CATEGORY", item: category }),
                        }))}
                        disabled={isBusy}
                    />

                    <article className="content-card dictionary-model-placeholder">
                        <div className="dictionary-section-header">
                            <div>
                                <h2 className="content-card-title">Modele</h2>
                                <p className="content-card-text">
                                    Wybierz markę, a następnie przypisz każdemu modelowi kategorię Vinted.
                                </p>
                            </div>
                        </div>

                        <div className="form-field">
                            <label className="form-label" htmlFor="manage-model-brand">Marka</label>
                            <AppSelect
                                id="manage-model-brand"
                                value={selectedBrandId}
                                options={brandOptions}
                                ariaLabel="Marka modeli"
                                disabled={isBusy || brands.length === 0}
                                onChange={(value) => {
                                    setSelectedBrandId(value);
                                    setErrorMessage(null);
                                    setSuccessMessage(null);
                                }}
                            />
                        </div>

                        <div className="dictionary-model-list-section">
                            {isModelsLoading ? (
                                <div className="dictionary-list-state">Pobieranie modeli...</div>
                            ) : models.length === 0 ? (
                                <div className="dictionary-list-state">Ta marka nie ma modeli.</div>
                            ) : (
                                <ul className="dictionary-list dictionary-model-list">
                                    {models.map((model) => (
                                        <li
                                            key={model.id}
                                            className="dictionary-list-item dictionary-model-row"
                                        >
                                            <div className="dictionary-item-content">
                                                <div className="dictionary-item-name">{model.name}</div>
                                                <div className="dictionary-item-path">
                                                    {model.categoryPath ?? "Brak przypisanej kategorii"}
                                                </div>
                                                <div className="dictionary-item-id">
                                                    ID: {model.id} · {model.brandName} · {model.targetMode === "SEARCH_QUERY" ? "Wyszukiwarka" : "Filtr Vinted"}
                                                </div>
                                            </div>

                                            <div className="dictionary-model-actions">
                                                <AppSelect
                                                    className="dictionary-category-select"
                                                    value={model.categoryId === null ? "" : String(model.categoryId)}
                                                    options={categoryOptions}
                                                    ariaLabel={`Kategoria dla ${model.name}`}
                                                    disabled={isBusy || categories.length === 0}
                                                    onChange={(value) => handleModelCategoryChange(model, value)}
                                                />
                                                <button
                                                    className="secondary-button"
                                                    type="button"
                                                    disabled={isBusy}
                                                    onClick={() => openEditDialog({ kind: "MODEL", item: model })}
                                                >
                                                    Edytuj
                                                </button>
                                                <button
                                                    className="bot-stop-button dictionary-delete-button"
                                                    type="button"
                                                    disabled={isBusy}
                                                    onClick={() => setDeleteTarget({ kind: "MODEL", item: model })}
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

            <AppDialog
                open={editTarget !== null}
                title={getEditTitle(editTarget)}
                description={getEditDescription(editTarget)}
                confirmLabel="Zapisz zmianę"
                busy={isBusy}
                input={editTarget === null ? undefined : {
                    label: editTarget.kind === "CATEGORY" ? "Pełna ścieżka" : "Nazwa",
                    value: editValue,
                    errorMessage: editError,
                    onChange: (value) => {
                        setEditValue(value);
                        setEditError(null);
                    },
                }}
                onCancel={closeEditDialog}
                onConfirm={() => void confirmEdit()}
            />

            <AppDialog
                open={deleteTarget !== null}
                title={getDeleteTitle(deleteTarget)}
                description={getDeleteDescription(deleteTarget)}
                confirmLabel="Usuń"
                danger
                busy={isBusy}
                onCancel={() => {
                    if (!isBusy) {
                        setDeleteTarget(null);
                    }
                }}
                onConfirm={() => void confirmDelete()}
            />
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

function DictionaryCard({
    title,
    emptyMessage,
    items,
    disabled,
}: {
    title: string;
    emptyMessage: string;
    items: DictionaryCardItem[];
    disabled: boolean;
}) {
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
                    {items.map((item) => (
                        <li key={item.id} className="dictionary-list-item">
                            <div className="dictionary-item-content">
                                <div className="dictionary-item-name">{item.title}</div>
                                <div className="dictionary-item-path">{item.subtitle}</div>
                            </div>
                            <div className="dictionary-card-actions">
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

function getEditTitle(target: EditTarget | null): string {
    if (target === null) {
        return "Edytuj wpis";
    }
    switch (target.kind) {
        case "BRAND": return "Edytuj markę";
        case "CATEGORY": return "Edytuj kategorię";
        case "MODEL": return "Edytuj model";
    }
}

function getEditDescription(target: EditTarget | null): string {
    if (target === null) {
        return "Wprowadź nową wartość.";
    }
    switch (target.kind) {
        case "BRAND": return `Zmieniasz markę „${target.item.name}”.`;
        case "CATEGORY": return `Zmieniasz kategorię „${target.item.path}”. Elementy ścieżki oddziel znakiem >.`;
        case "MODEL": return `Zmieniasz model „${target.item.name}”.`;
    }
}

function getDeleteTitle(target: DeleteTarget | null): string {
    if (target === null) {
        return "Usunąć wpis?";
    }
    switch (target.kind) {
        case "BRAND": return "Usunąć markę?";
        case "CATEGORY": return "Usunąć kategorię?";
        case "MODEL": return "Usunąć model?";
    }
}

function getDeleteDescription(target: DeleteTarget | null): string {
    if (target === null) {
        return "Tej operacji nie można cofnąć.";
    }
    switch (target.kind) {
        case "BRAND":
            return `Marka „${target.item.name}” może zostać usunięta tylko wtedy, gdy nie ma modeli i nie jest używana przez bota.`;
        case "CATEGORY":
            return `Kategoria „${target.item.path}” może zostać usunięta tylko wtedy, gdy nie jest używana przez żadnego bota ani model.`;
        case "MODEL":
            return `Model „${target.item.name}” może zostać usunięty tylko wtedy, gdy nie jest używany przez żadnego bota.`;
    }
}

function parseCategoryPath(value: string): string[] {
    return value
        .split(">")
        .map(normalizeText)
        .filter((element) => element.length > 0);
}

function normalizeText(value: string): string {
    return value.trim().replace(/\s+/g, " ");
}

function getErrorMessage(error: unknown, fallbackMessage: string): string {
    return error instanceof Error ? error.message : fallbackMessage;
}

export default ManageDictionariesPage;

import {
    useCallback,
    useEffect,
    useMemo,
    useState,
} from "react";

import {
    getBrands,
    getModelsByBrand,
    updateModelPricing,
} from "../api/dictionariesApi";

import type {
    DictionaryBrand,
    DictionaryModel,
} from "../types/dictionaries";

import "../styles/price-matrix.css";

interface PriceDraft {
    proposedOfferPrice: string;
    expectedResalePrice: string;
}

function PriceMatrixPage() {
    const [brands, setBrands] = useState<DictionaryBrand[]>([]);
    const [modelsByBrand, setModelsByBrand] =
        useState<Record<number, DictionaryModel[]>>({});
    const [drafts, setDrafts] =
        useState<Record<number, PriceDraft>>({});
    const [savingModelId, setSavingModelId] =
        useState<number | null>(null);
    const [savedModelId, setSavedModelId] =
        useState<number | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] =
        useState<string | null>(null);

    const loadData = useCallback(async () => {
        setIsLoading(true);
        setErrorMessage(null);

        try {
            const loadedBrands = [...await getBrands()].sort((left, right) =>
                left.name.localeCompare(right.name, "pl"),
            );

            const modelGroups = await Promise.all(
                loadedBrands.map(async (brand) => ({
                    brandId: brand.id,
                    models: [...await getModelsByBrand(brand.id)].sort((left, right) =>
                        left.name.localeCompare(right.name, "pl"),
                    ),
                })),
            );

            const nextModelsByBrand: Record<number, DictionaryModel[]> = {};
            const nextDrafts: Record<number, PriceDraft> = {};

            for (const group of modelGroups) {
                nextModelsByBrand[group.brandId] = group.models;

                for (const model of group.models) {
                    nextDrafts[model.id] = {
                        proposedOfferPrice: formatInputPrice(model.proposedOfferPrice),
                        expectedResalePrice: formatInputPrice(model.expectedResalePrice),
                    };
                }
            }

            setBrands(loadedBrands);
            setModelsByBrand(nextModelsByBrand);
            setDrafts(nextDrafts);
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Nie udało się pobrać cennika modeli."));
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadData();
    }, [loadData]);

    const modelCount = useMemo(
        () => Object.values(modelsByBrand)
            .reduce((sum, models) => sum + models.length, 0),
        [modelsByBrand],
    );

    function updateDraft(
        modelId: number,
        field: keyof PriceDraft,
        value: string,
    ) {
        setDrafts((current) => ({
            ...current,
            [modelId]: {
                ...(current[modelId] ?? {
                    proposedOfferPrice: "",
                    expectedResalePrice: "",
                }),
                [field]: value,
            },
        }));
        setSavedModelId(null);
    }

    async function saveModel(model: DictionaryModel) {
        const draft = drafts[model.id];

        if (draft === undefined || savingModelId !== null) {
            return;
        }

        const proposedResult = parseOptionalPositivePrice(
            draft.proposedOfferPrice,
            "Proponowana cena dla bota musi być większa od 0.",
        );

        if (!proposedResult.valid) {
            setErrorMessage(proposedResult.errorMessage);
            return;
        }

        const resaleResult = parseOptionalPositivePrice(
            draft.expectedResalePrice,
            "Cena sprzedaży musi być większa od 0.",
        );

        if (!resaleResult.valid) {
            setErrorMessage(resaleResult.errorMessage);
            return;
        }

        if (
            samePrice(model.proposedOfferPrice, proposedResult.value)
            && samePrice(model.expectedResalePrice, resaleResult.value)
        ) {
            return;
        }

        setSavingModelId(model.id);
        setSavedModelId(null);
        setErrorMessage(null);

        try {
            const updated = await updateModelPricing(
                model.brandId,
                model.id,
                {
                    proposedOfferPrice: proposedResult.value,
                    expectedResalePrice: resaleResult.value,
                },
            );

            setModelsByBrand((current) => ({
                ...current,
                [model.brandId]: (current[model.brandId] ?? []).map((item) =>
                    item.id === updated.id
                        ? updated
                        : item,
                ),
            }));

            setDrafts((current) => ({
                ...current,
                [updated.id]: {
                    proposedOfferPrice: formatInputPrice(updated.proposedOfferPrice),
                    expectedResalePrice: formatInputPrice(updated.expectedResalePrice),
                },
            }));

            setSavedModelId(updated.id);
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Nie udało się zapisać cen modelu."));
        } finally {
            setSavingModelId(null);
        }
    }

    return (
        <section className="page">
            <header className="page-header price-matrix-page-header">
                <div>
                    <p className="page-eyebrow">Planowanie zakupów</p>
                    <h1 className="page-title">Cennik modeli</h1>
                    <p className="page-description">
                        Arkusz referencyjny generowany automatycznie ze słownika modeli.
                        Wpisz cenę, którą chcesz proponować jako punkt odniesienia dla bota,
                        oraz orientacyjną cenę późniejszej sprzedaży.
                    </p>
                </div>

                <div className="price-matrix-summary">
                    <strong>{modelCount}</strong>
                    <span>modeli</span>
                </div>
            </header>

            {errorMessage !== null && (
                <div className="form-message form-message-error" role="alert">
                    {errorMessage}
                </div>
            )}

            {isLoading ? (
                <article className="content-card">
                    <div className="dictionary-list-state">Pobieranie cennika...</div>
                </article>
            ) : brands.length === 0 ? (
                <article className="content-card">
                    <div className="dictionary-list-state">
                        Najpierw dodaj marki i modele w słownikach.
                    </div>
                </article>
            ) : (
                <article className="content-card price-matrix-card">
                    <div className="price-matrix-scroll">
                        <div className="price-matrix-board">
                            {brands.map((brand) => (
                                <BrandPriceSheet
                                    key={brand.id}
                                    brand={brand}
                                    models={modelsByBrand[brand.id] ?? []}
                                    drafts={drafts}
                                    savingModelId={savingModelId}
                                    savedModelId={savedModelId}
                                    onDraftChange={updateDraft}
                                    onSave={saveModel}
                                />
                            ))}
                        </div>
                    </div>
                </article>
            )}
        </section>
    );
}

interface BrandPriceSheetProps {
    brand: DictionaryBrand;
    models: DictionaryModel[];
    drafts: Record<number, PriceDraft>;
    savingModelId: number | null;
    savedModelId: number | null;
    onDraftChange: (
        modelId: number,
        field: keyof PriceDraft,
        value: string,
    ) => void;
    onSave: (model: DictionaryModel) => Promise<void>;
}

function BrandPriceSheet({
    brand,
    models,
    drafts,
    savingModelId,
    savedModelId,
    onDraftChange,
    onSave,
}: BrandPriceSheetProps) {
    return (
        <section className="price-brand-sheet">
            <div className="price-brand-title">{brand.name}</div>

            <div className="price-sheet-row price-sheet-header">
                <div>Model</div>
                <div>Proponowana cena</div>
                <div>Sprzedaż</div>
            </div>

            {models.length === 0 ? (
                <div className="price-sheet-empty">Brak modeli</div>
            ) : models.map((model) => {
                const draft = drafts[model.id] ?? {
                    proposedOfferPrice: "",
                    expectedResalePrice: "",
                };

                return (
                    <div className="price-sheet-row" key={model.id}>
                        <div className="price-model-cell">
                            <strong>{model.name}</strong>
                            <span>
                                {model.targetMode === "SEARCH_QUERY"
                                    ? "Wyszukiwarka"
                                    : "Filtr Vinted"}
                            </span>
                        </div>

                        <PriceInput
                            value={draft.proposedOfferPrice}
                            disabled={savingModelId !== null && savingModelId !== model.id}
                            saving={savingModelId === model.id}
                            saved={savedModelId === model.id}
                            onChange={(value) =>
                                onDraftChange(model.id, "proposedOfferPrice", value)
                            }
                            onBlur={() => void onSave(model)}
                        />

                        <PriceInput
                            value={draft.expectedResalePrice}
                            disabled={savingModelId !== null && savingModelId !== model.id}
                            saving={savingModelId === model.id}
                            saved={savedModelId === model.id}
                            onChange={(value) =>
                                onDraftChange(model.id, "expectedResalePrice", value)
                            }
                            onBlur={() => void onSave(model)}
                        />
                    </div>
                );
            })}
        </section>
    );
}

interface PriceInputProps {
    value: string;
    disabled: boolean;
    saving: boolean;
    saved: boolean;
    onChange: (value: string) => void;
    onBlur: () => void;
}

function PriceInput({
    value,
    disabled,
    saving,
    saved,
    onChange,
    onBlur,
}: PriceInputProps) {
    return (
        <div className="price-input-cell">
            <div className="price-input-wrapper">
                <input
                    className="price-matrix-input"
                    type="number"
                    min="0.01"
                    step="0.01"
                    value={value}
                    disabled={disabled || saving}
                    placeholder="—"
                    onChange={(event) => onChange(event.target.value)}
                    onBlur={onBlur}
                    onKeyDown={(event) => {
                        if (event.key === "Enter") {
                            event.currentTarget.blur();
                        }
                    }}
                />
                {value.trim().length > 0 && <span className="price-currency">zł</span>}
            </div>
            {saving && <span className="price-save-state">Zapisywanie...</span>}
            {!saving && saved && <span className="price-save-state">Zapisano</span>}
        </div>
    );
}

interface ParsedPriceSuccess {
    valid: true;
    value: number | null;
}

interface ParsedPriceFailure {
    valid: false;
    errorMessage: string;
}

function parseOptionalPositivePrice(
    rawValue: string,
    errorMessage: string,
): ParsedPriceSuccess | ParsedPriceFailure {
    const normalized = rawValue.trim().replace(",", ".");

    if (normalized.length === 0) {
        return { valid: true, value: null };
    }

    const value = Number(normalized);

    if (!Number.isFinite(value) || value <= 0) {
        return { valid: false, errorMessage };
    }

    return { valid: true, value };
}

function formatInputPrice(value: number | null): string {
    return value === null
        ? ""
        : String(value);
}

function samePrice(left: number | null, right: number | null): boolean {
    if (left === null || right === null) {
        return left === right;
    }

    return Math.abs(left - right) < 0.0001;
}

function getErrorMessage(error: unknown, fallback: string): string {
    return error instanceof Error
        ? error.message
        : fallback;
}

export default PriceMatrixPage;

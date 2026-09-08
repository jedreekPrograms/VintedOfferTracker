import AppSelect, {
    type AppSelectOption,
} from "../../../components/AppSelect";

import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../../../types/dictionaries";

import type {
    TargetMode,
} from "../../../types/bots";

interface BotFiltersSectionProps {
    categories: DictionaryCategory[];
    brands: DictionaryBrand[];
    models: DictionaryModel[];

    selectedCategoryId: string;
    selectedBrandId: string;

    targetMode: TargetMode;
    selectedModelId: string;
    searchQuery: string;

    minPrice: string;
    maxPrice: string;

    isLoadingDictionaries: boolean;
    areModelsLoading: boolean;
    targetFieldsDisabled?: boolean;

    onCategoryChange: (categoryId: string) => void;
    onBrandChange: (brandId: string) => void;
    onTargetModeChange: (targetMode: TargetMode) => void;
    onModelChange: (modelId: string) => void;
    onSearchQueryChange: (value: string) => void;
    onMinPriceChange: (value: string) => void;
    onMaxPriceChange: (value: string) => void;
}

function BotFiltersSection({
    categories,
    brands,
    models,
    selectedCategoryId,
    selectedBrandId,
    selectedModelId,
    minPrice,
    maxPrice,
    isLoadingDictionaries,
    areModelsLoading,
    targetFieldsDisabled = false,
    onCategoryChange,
    onBrandChange,
    onModelChange,
    onMinPriceChange,
    onMaxPriceChange,
}: BotFiltersSectionProps) {
    const selectedCategory = categories.find(
        category => String(category.id) === selectedCategoryId,
    ) ?? null;

    const selectedBrand = brands.find(
        brand => String(brand.id) === selectedBrandId,
    ) ?? null;

    const selectedModel = models.find(
        model => String(model.id) === selectedModelId,
    ) ?? null;

    const referenceDifference =
        selectedModel?.proposedOfferPrice !== null
        && selectedModel?.proposedOfferPrice !== undefined
        && selectedModel?.expectedResalePrice !== null
        && selectedModel?.expectedResalePrice !== undefined
            ? selectedModel.expectedResalePrice - selectedModel.proposedOfferPrice
            : null;

    const categoryOptions: AppSelectOption[] = [
        {
            value: "",
            label: "Wybierz kategorię",
        },
        ...categories.map(category => ({
            value: String(category.id),
            label: category.path,
        })),
    ];

    const brandOptions: AppSelectOption[] = [
        {
            value: "",
            label: "Wybierz markę",
        },
        ...brands.map(brand => ({
            value: String(brand.id),
            label: brand.name,
        })),
    ];

    const modelOptions: AppSelectOption[] = [
        {
            value: "",
            label: areModelsLoading
                ? "Pobieranie modeli..."
                : "Wybierz model",
        },
        ...models.map(model => ({
            value: String(model.id),
            label: `${model.name}${model.targetMode === "SEARCH_QUERY" ? " · wyszukiwarka" : ""}`,
        })),
    ];

    return (
        <article className="content-card">
            <div className="bot-form-section-header">
                <div>
                    <span className="bot-form-step">3</span>
                    <h2 className="content-card-title">Filtry ofert</h2>
                </div>

                <p className="content-card-text">
                    Wybierz kategorię, markę i model ze słownika. Model sam określa,
                    czy Playwright użyje filtra Vinted, czy wpisze jego nazwę do wyszukiwarki.
                </p>
            </div>

            {targetFieldsDisabled && (
                <div className="information-box">
                    Kategoria, marka i model są zablokowane przy aktywnych negocjacjach,
                    ale nadal możesz zmienić zakres cen dla nowych ofert.
                </div>
            )}

            {selectedModel !== null && (
                <div className="information-box">
                    <div className="bot-filter-reference-grid">
                        <div className="bot-filter-reference-primary">
                            <strong>{selectedModel.brandName} → {selectedModel.name}</strong>
                            <div className="form-help">
                                Tryb: {selectedModel.targetMode === "SEARCH_QUERY"
                                    ? "wyszukiwanie tekstowe"
                                    : "model z listy Vinted"}
                            </div>
                        </div>

                        <ReferencePrice
                            label="Proponowana cena dla bota"
                            value={selectedModel.proposedOfferPrice}
                        />

                        <ReferencePrice
                            label="Planowana sprzedaż"
                            value={selectedModel.expectedResalePrice}
                        />

                        <ReferencePrice
                            label="Orientacyjna różnica"
                            value={referenceDifference}
                        />
                    </div>
                </div>
            )}

            {isLoadingDictionaries ? (
                <div className="dictionary-list-state">Pobieranie słowników...</div>
            ) : (
                <>
                    <div className="bot-form-grid bot-form-grid-three">
                        <div className="form-field">
                            <label className="form-label" htmlFor="bot-category">
                                Kategoria
                            </label>
                            <AppSelect
                                id="bot-category"
                                value={selectedCategoryId}
                                options={categoryOptions}
                                ariaLabel="Kategoria bota"
                                disabled={targetFieldsDisabled}
                                onChange={onCategoryChange}
                            />
                        </div>

                        <div className="form-field">
                            <label className="form-label" htmlFor="bot-brand">
                                Marka
                            </label>
                            <AppSelect
                                id="bot-brand"
                                value={selectedBrandId}
                                options={brandOptions}
                                ariaLabel="Marka bota"
                                disabled={targetFieldsDisabled}
                                onChange={onBrandChange}
                            />
                        </div>

                        <div className="form-field">
                            <label className="form-label" htmlFor="bot-model">
                                Model
                            </label>
                            <AppSelect
                                id="bot-model"
                                value={selectedModelId}
                                options={modelOptions}
                                ariaLabel="Model bota"
                                disabled={
                                    targetFieldsDisabled
                                    || selectedBrandId.length === 0
                                    || areModelsLoading
                                }
                                onChange={onModelChange}
                            />
                            {selectedModel !== null && (
                                <span className="form-help">
                                    {selectedModel.targetMode === "SEARCH_QUERY"
                                        ? `Playwright wpisze „${selectedModel.name}” do wyszukiwarki Vinted.`
                                        : "Playwright wybierze dokładny model z filtra Vinted."}
                                </span>
                            )}
                        </div>
                    </div>

                    <div className="bot-form-grid bot-form-grid-three bot-form-price-grid">
                        <div className="form-field">
                            <label className="form-label" htmlFor="min-price">
                                Cena minimalna
                            </label>
                            <input
                                id="min-price"
                                className="form-input"
                                type="number"
                                min="0"
                                step="0.01"
                                value={minPrice}
                                placeholder="np. 1000"
                                onChange={event => onMinPriceChange(event.target.value)}
                            />
                        </div>

                        <div className="form-field">
                            <label className="form-label" htmlFor="max-price">
                                Cena maksymalna
                            </label>
                            <input
                                id="max-price"
                                className="form-input"
                                type="number"
                                min="0"
                                step="0.01"
                                value={maxPrice}
                                placeholder="np. 2500"
                                onChange={event => onMaxPriceChange(event.target.value)}
                            />
                        </div>

                        <div className="form-field">
                            <span className="form-label">Sposób wyszukiwania</span>
                            <div className="bot-selection-preview">
                                <strong>
                                    {selectedModel === null
                                        ? "Wybierz model"
                                        : selectedModel.targetMode === "SEARCH_QUERY"
                                            ? "Wyszukiwanie tekstowe"
                                            : "Filtr modelu Vinted"}
                                </strong>
                            </div>
                        </div>
                    </div>

                    {selectedCategory !== null && (
                        <div className="bot-selection-preview">
                            <span className="bot-selection-preview-label">Wybrana kategoria</span>
                            <strong>{selectedCategory.path}</strong>
                        </div>
                    )}

                    {selectedBrand !== null && selectedModel !== null && (
                        <div className="bot-selection-preview">
                            <span className="bot-selection-preview-label">Wybrany produkt</span>
                            <strong>{selectedBrand.name} → {selectedModel.name}</strong>
                        </div>
                    )}
                </>
            )}
        </article>
    );
}

interface ReferencePriceProps {
    label: string;
    value: number | null;
}

function ReferencePrice({ label, value }: ReferencePriceProps) {
    return (
        <div>
            <div className="form-help">{label}</div>
            <strong>{value === null ? "—" : `${formatPrice(value)} zł`}</strong>
        </div>
    );
}

function formatPrice(value: number): string {
    return new Intl.NumberFormat("pl-PL", {
        maximumFractionDigits: 2,
    }).format(value);
}

export default BotFiltersSection;

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

    onCategoryChange: (
        categoryId: string,
    ) => void;

    onBrandChange: (
        brandId: string,
    ) => void;

    onTargetModeChange: (
        targetMode: TargetMode,
    ) => void;

    onModelChange: (
        modelId: string,
    ) => void;

    onSearchQueryChange: (
        value: string,
    ) => void;

    onMinPriceChange: (
        value: string,
    ) => void;

    onMaxPriceChange: (
        value: string,
    ) => void;
}

function BotFiltersSection({
    categories,
    brands,
    models,

    selectedCategoryId,
    selectedBrandId,

    targetMode,

    selectedModelId,

    searchQuery,

    minPrice,
    maxPrice,

    isLoadingDictionaries,
    areModelsLoading,

    onCategoryChange,
    onBrandChange,
    onTargetModeChange,
    onModelChange,
    onSearchQueryChange,

    onMinPriceChange,
    onMaxPriceChange,
}: BotFiltersSectionProps) {
    const selectedCategory =
        categories.find(
            (category) =>
                String(category.id)
                === selectedCategoryId,
        ) ?? null;

    const selectedBrand =
        brands.find(
            (brand) =>
                String(brand.id)
                === selectedBrandId,
        ) ?? null;

    const selectedModel =
        models.find(
            (model) =>
                String(model.id)
                === selectedModelId,
        ) ?? null;

    const normalizedSearchQuery =
        searchQuery
            .trim()
            .replace(
                /\s+/g,
                " ",
            );

    return (
        <article className="content-card">
            <div className="bot-form-section-header">
                <div>
                    <span className="bot-form-step">
                        3
                    </span>

                    <h2 className="content-card-title">
                        Filtry ofert
                    </h2>
                </div>

                <p className="content-card-text">
                    Kategoria i marka pochodzą
                    z ręcznie utworzonych słowników.
                    Model możesz wybrać z Vinted
                    albo wyszukiwać własną frazą.
                </p>
            </div>

            {isLoadingDictionaries ? (
                <div className="dictionary-list-state">
                    Pobieranie słowników...
                </div>
            ) : (
                <>
                    <div className="bot-form-grid bot-form-grid-three">
                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="bot-category"
                            >
                                Kategoria
                            </label>

                            <select
                                id="bot-category"
                                className="form-select"
                                value={
                                    selectedCategoryId
                                }
                                onChange={(event) => {
                                    onCategoryChange(
                                        event.target.value,
                                    );
                                }}
                            >
                                <option value="">
                                    Wybierz kategorię
                                </option>

                                {categories.map(
                                    (category) => (
                                        <option
                                            key={
                                                category.id
                                            }
                                            value={
                                                category.id
                                            }
                                        >
                                            {category.path}
                                        </option>
                                    ),
                                )}
                            </select>
                        </div>

                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="bot-brand"
                            >
                                Marka
                            </label>

                            <select
                                id="bot-brand"
                                className="form-select"
                                value={
                                    selectedBrandId
                                }
                                onChange={(event) => {
                                    onBrandChange(
                                        event.target.value,
                                    );
                                }}
                            >
                                <option value="">
                                    Wybierz markę
                                </option>

                                {brands.map(
                                    (brand) => (
                                        <option
                                            key={
                                                brand.id
                                            }
                                            value={
                                                brand.id
                                            }
                                        >
                                            {brand.name}
                                        </option>
                                    ),
                                )}
                            </select>
                        </div>

                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="target-mode"
                            >
                                Sposób wyszukiwania modelu
                            </label>

                            <select
                                id="target-mode"
                                className="form-select"
                                value={
                                    targetMode
                                }
                                onChange={(event) => {
                                    onTargetModeChange(
                                        event.target.value as TargetMode,
                                    );
                                }}
                            >
                                <option value="VINTED_MODEL">
                                    Model dostępny na Vinted
                                </option>

                                <option value="SEARCH_QUERY">
                                    Własna fraza wyszukiwania
                                </option>
                            </select>
                        </div>
                    </div>

                    <div className="bot-form-grid bot-form-grid-three">
                        <div className="form-field">
                            {targetMode === "VINTED_MODEL" ? (
                                <>
                                    <label
                                        className="form-label"
                                        htmlFor="bot-model"
                                    >
                                        Model
                                    </label>

                                    <select
                                        id="bot-model"
                                        className="form-select"
                                        value={
                                            selectedModelId
                                        }
                                        disabled={
                                            selectedBrandId
                                                .length === 0
                                            || areModelsLoading
                                        }
                                        onChange={(event) => {
                                            onModelChange(
                                                event.target.value,
                                            );
                                        }}
                                    >
                                        <option value="">
                                            {areModelsLoading
                                                ? "Pobieranie modeli..."
                                                : "Wybierz model"}
                                        </option>

                                        {models.map(
                                            (model) => (
                                                <option
                                                    key={
                                                        model.id
                                                    }
                                                    value={
                                                        model.id
                                                    }
                                                >
                                                    {model.name}
                                                </option>
                                            ),
                                        )}
                                    </select>

                                    <span className="form-help">
                                        Bot wybierze dokładny model
                                        z filtra Vinted.
                                    </span>
                                </>
                            ) : (
                                <>
                                    <label
                                        className="form-label"
                                        htmlFor="bot-search-query"
                                    >
                                        Fraza wyszukiwania
                                    </label>

                                    <input
                                        id="bot-search-query"
                                        className="form-input"
                                        type="text"
                                        value={
                                            searchQuery
                                        }
                                        maxLength={255}
                                        placeholder="np. Galaxy Tab S11 Ultra"
                                        onChange={(event) => {
                                            onSearchQueryChange(
                                                event.target.value,
                                            );
                                        }}
                                    />

                                    <span className="form-help">
                                        Użyj tego trybu, gdy Vinted
                                        nie ma jeszcze danego modelu
                                        na swojej liście.
                                    </span>
                                </>
                            )}
                        </div>

                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="min-price"
                            >
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
                                onChange={(event) => {
                                    onMinPriceChange(
                                        event.target.value,
                                    );
                                }}
                            />
                        </div>

                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="max-price"
                            >
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
                                onChange={(event) => {
                                    onMaxPriceChange(
                                        event.target.value,
                                    );
                                }}
                            />
                        </div>
                    </div>

                    {selectedCategory !== null && (
                        <div className="bot-selection-preview">
                            <span className="bot-selection-preview-label">
                                Wybrana kategoria
                            </span>

                            <strong>
                                {selectedCategory.path}
                            </strong>
                        </div>
                    )}

                    {targetMode === "VINTED_MODEL"
                        && selectedBrand !== null
                        && selectedModel !== null && (
                        <div className="bot-selection-preview">
                            <span className="bot-selection-preview-label">
                                Wybrany produkt
                            </span>

                            <strong>
                                {selectedBrand.name}
                                {" → "}
                                {selectedModel.name}
                            </strong>
                        </div>
                    )}

                    {targetMode === "SEARCH_QUERY"
                        && selectedBrand !== null
                        && normalizedSearchQuery.length > 0 && (
                        <div className="bot-selection-preview">
                            <span className="bot-selection-preview-label">
                                Wyszukiwanie tekstowe
                            </span>

                            <strong>
                                {selectedBrand.name}
                                {" → "}
                                {normalizedSearchQuery}
                            </strong>
                        </div>
                    )}

                    {targetMode === "SEARCH_QUERY" && (
                        <div className="information-box">
                            W kolejnym etapie Playwright
                            będzie wpisywał tę frazę
                            do wyszukiwarki Vinted.
                            Przed wysłaniem oferty
                            dodatkowy matcher sprawdzi,
                            czy tytuł rzeczywiście pasuje
                            do oczekiwanego modelu.
                        </div>
                    )}
                </>
            )}
        </article>
    );
}

export default BotFiltersSection;

import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../../../types/dictionaries";

interface BotFiltersSectionProps {
    categories: DictionaryCategory[];
    brands: DictionaryBrand[];
    models: DictionaryModel[];

    selectedCategoryId: string;
    selectedBrandId: string;
    selectedModelId: string;

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

    onModelChange: (
        modelId: string,
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
    selectedModelId,

    minPrice,
    maxPrice,

    isLoadingDictionaries,
    areModelsLoading,

    onCategoryChange,
    onBrandChange,
    onModelChange,

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
                    Kategorie, marki i modele
                    pochodzą z ręcznie utworzonych
                    słowników.
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
                        </div>
                    </div>

                    <div className="bot-form-grid bot-form-grid-two bot-form-price-grid">
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

                    {selectedBrand !== null
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
                </>
            )}
        </article>
    );
}

export default BotFiltersSection;
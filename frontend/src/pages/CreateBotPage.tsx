import {
    type FormEvent,
    useCallback,
    useEffect,
    useState,
} from "react";
import {
    getBrands,
    getCategories,
    getModelsByBrand,
} from "../api/dictionariesApi";
import type {
    DictionaryBrand,
    DictionaryCategory,
    DictionaryModel,
} from "../types/dictionaries";

function CreateBotPage() {
    const [botName, setBotName] =
        useState("");

    const [email, setEmail] =
        useState("");

    const [password, setPassword] =
        useState("");

    const [categories, setCategories] =
        useState<DictionaryCategory[]>([]);

    const [brands, setBrands] =
        useState<DictionaryBrand[]>([]);

    const [models, setModels] =
        useState<DictionaryModel[]>([]);

    const [selectedCategoryId, setSelectedCategoryId] =
        useState("");

    const [selectedBrandId, setSelectedBrandId] =
        useState("");

    const [selectedModelId, setSelectedModelId] =
        useState("");

    const [minPrice, setMinPrice] =
        useState("");

    const [maxPrice, setMaxPrice] =
        useState("");

    const [dailyNegotiationBudget, setDailyNegotiationBudget] =
        useState("25");

    const [isLoadingDictionaries, setIsLoadingDictionaries] =
        useState(true);

    const [areModelsLoading, setAreModelsLoading] =
        useState(false);

    const [errorMessage, setErrorMessage] =
        useState<string | null>(null);

    const loadDictionaries = useCallback(async () => {
        setIsLoadingDictionaries(true);
        setErrorMessage(null);

        try {
            const [
                loadedCategories,
                loadedBrands,
            ] = await Promise.all([
                getCategories(),
                getBrands(),
            ]);

            setCategories(loadedCategories);
            setBrands(loadedBrands);
        } catch (error) {
            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się pobrać słowników.",
                ),
            );
        } finally {
            setIsLoadingDictionaries(false);
        }
    }, []);

    const loadModels = useCallback(
        async (brandId: number) => {
            setAreModelsLoading(true);
            setErrorMessage(null);

            try {
                const loadedModels =
                    await getModelsByBrand(
                        brandId,
                    );

                setModels(loadedModels);
            } catch (error) {
                setModels([]);

                setErrorMessage(
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
        void loadDictionaries();
    }, [loadDictionaries]);

    useEffect(() => {
        if (selectedBrandId.length === 0) {
            setModels([]);
            setSelectedModelId("");

            return;
        }

        setSelectedModelId("");

        void loadModels(
            Number(selectedBrandId),
        );
    }, [
        selectedBrandId,
        loadModels,
    ]);

    function handleSubmit(
        event: FormEvent<HTMLFormElement>,
    ) {
        event.preventDefault();

        setErrorMessage(null);

        const normalizedBotName =
            botName.trim();

        const normalizedEmail =
            email.trim();

        if (normalizedBotName.length === 0) {
            setErrorMessage(
                "Wpisz nazwę bota.",
            );

            return;
        }

        if (normalizedEmail.length === 0) {
            setErrorMessage(
                "Wpisz e-mail konta Vinted.",
            );

            return;
        }

        if (password.length === 0) {
            setErrorMessage(
                "Wpisz hasło konta Vinted.",
            );

            return;
        }

        if (selectedCategoryId.length === 0) {
            setErrorMessage(
                "Wybierz kategorię.",
            );

            return;
        }

        if (selectedBrandId.length === 0) {
            setErrorMessage(
                "Wybierz markę.",
            );

            return;
        }

        if (selectedModelId.length === 0) {
            setErrorMessage(
                "Wybierz model.",
            );

            return;
        }

        const parsedMinPrice =
            Number(minPrice);

        const parsedMaxPrice =
            Number(maxPrice);

        if (
            !Number.isFinite(parsedMinPrice)
            || parsedMinPrice < 0
        ) {
            setErrorMessage(
                "Minimalna cena jest nieprawidłowa.",
            );

            return;
        }

        if (
            !Number.isFinite(parsedMaxPrice)
            || parsedMaxPrice <= 0
        ) {
            setErrorMessage(
                "Maksymalna cena jest nieprawidłowa.",
            );

            return;
        }

        if (parsedMinPrice > parsedMaxPrice) {
            setErrorMessage(
                "Minimalna cena nie może być większa od maksymalnej.",
            );

            return;
        }

        const parsedBudget =
            Number(dailyNegotiationBudget);

        if (
            !Number.isInteger(parsedBudget)
            || parsedBudget < 1
            || parsedBudget > 25
        ) {
            setErrorMessage(
                "Dzienny budżet negocjacyjny musi być liczbą od 1 do 25.",
            );

            return;
        }

        /*
         * Na razie formularza nie wysyłamy.
         *
         * W następnym kroku podłączymy dokładny endpoint backendu
         * i dodamy negotiationSteps.
         */
        console.log({
            botName: normalizedBotName,
            email: normalizedEmail,
            category:
                selectedCategory,
            brand:
                selectedBrand,
            model:
                selectedModel,
            minPrice: parsedMinPrice,
            maxPrice: parsedMaxPrice,
            dailyNegotiationBudget:
                parsedBudget,
        });

        setErrorMessage(
            "Formularz jest poprawny. W następnym kroku podłączymy konfigurację negocjacji i zapis bota.",
        );
    }

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
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">
                        Konfiguracja
                    </p>

                    <h1 className="page-title">
                        Utwórz bota
                    </h1>

                    <p className="page-description">
                        Każdy bot korzysta z jednego, osobnego
                        konta Vinted i posiada własną konfigurację
                        wyszukiwania oraz negocjacji.
                    </p>
                </div>
            </header>

            <form
                className="bot-form"
                onSubmit={handleSubmit}
            >
                <article className="content-card">
                    <div className="bot-form-section-header">
                        <div>
                            <span className="bot-form-step">
                                1
                            </span>

                            <h2 className="content-card-title">
                                Podstawowe informacje
                            </h2>
                        </div>

                        <p className="content-card-text">
                            Nazwa służy tylko do rozpoznawania
                            bota w naszym panelu.
                        </p>
                    </div>

                    <div className="bot-form-grid">
                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="bot-name"
                            >
                                Nazwa bota
                            </label>

                            <input
                                id="bot-name"
                                className="form-input"
                                type="text"
                                value={botName}
                                maxLength={255}
                                placeholder="np. Samsung S25 do 2000 zł"
                                onChange={(event) => {
                                    setBotName(
                                        event.target.value,
                                    );

                                    setErrorMessage(null);
                                }}
                            />
                        </div>
                    </div>
                </article>

                <article className="content-card">
                    <div className="bot-form-section-header">
                        <div>
                            <span className="bot-form-step">
                                2
                            </span>

                            <h2 className="content-card-title">
                                Konto Vinted
                            </h2>
                        </div>

                        <p className="content-card-text">
                            Jedno konto Vinted może należeć
                            wyłącznie do jednego bota.
                        </p>
                    </div>

                    <div className="bot-form-grid bot-form-grid-two">
                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="vinted-email"
                            >
                                E-mail
                            </label>

                            <input
                                id="vinted-email"
                                className="form-input"
                                type="email"
                                value={email}
                                autoComplete="username"
                                placeholder="konto@example.com"
                                onChange={(event) => {
                                    setEmail(
                                        event.target.value,
                                    );

                                    setErrorMessage(null);
                                }}
                            />
                        </div>

                        <div className="form-field">
                            <label
                                className="form-label"
                                htmlFor="vinted-password"
                            >
                                Hasło
                            </label>

                            <input
                                id="vinted-password"
                                className="form-input"
                                type="password"
                                value={password}
                                autoComplete="current-password"
                                placeholder="Hasło do konta Vinted"
                                onChange={(event) => {
                                    setPassword(
                                        event.target.value,
                                    );

                                    setErrorMessage(null);
                                }}
                            />
                        </div>
                    </div>

                    <div className="information-box">
                        Dane logowania dotyczą tylko konta
                        przypisanego do tego konkretnego bota.
                    </div>
                </article>

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
                            Kategorie, marki i modele pochodzą
                            z ręcznie utworzonych słowników.
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
                                        value={selectedCategoryId}
                                        onChange={(event) => {
                                            setSelectedCategoryId(
                                                event.target.value,
                                            );

                                            setErrorMessage(null);
                                        }}
                                    >
                                        <option value="">
                                            Wybierz kategorię
                                        </option>

                                        {categories.map(
                                            (category) => (
                                                <option
                                                    key={category.id}
                                                    value={category.id}
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
                                        value={selectedBrandId}
                                        onChange={(event) => {
                                            setSelectedBrandId(
                                                event.target.value,
                                            );

                                            setErrorMessage(null);
                                        }}
                                    >
                                        <option value="">
                                            Wybierz markę
                                        </option>

                                        {brands.map(
                                            (brand) => (
                                                <option
                                                    key={brand.id}
                                                    value={brand.id}
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
                                        value={selectedModelId}
                                        disabled={
                                            selectedBrandId.length === 0
                                            || areModelsLoading
                                        }
                                        onChange={(event) => {
                                            setSelectedModelId(
                                                event.target.value,
                                            );

                                            setErrorMessage(null);
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
                                                    key={model.id}
                                                    value={model.id}
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
                                        placeholder="np. 500"
                                        onChange={(event) => {
                                            setMinPrice(
                                                event.target.value,
                                            );

                                            setErrorMessage(null);
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
                                        placeholder="np. 2000"
                                        onChange={(event) => {
                                            setMaxPrice(
                                                event.target.value,
                                            );

                                            setErrorMessage(null);
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

                <article className="content-card">
                    <div className="bot-form-section-header">
                        <div>
                            <span className="bot-form-step">
                                4
                            </span>

                            <h2 className="content-card-title">
                                Budżet negocjacyjny
                            </h2>
                        </div>

                        <p className="content-card-text">
                            Budżet określa maksymalną liczbę
                            zarezerwowanych kroków negocjacji.
                        </p>
                    </div>

                    <div className="bot-form-grid">
                        <div className="form-field bot-budget-field">
                            <label
                                className="form-label"
                                htmlFor="negotiation-budget"
                            >
                                Dzienny budżet
                            </label>

                            <input
                                id="negotiation-budget"
                                className="form-input"
                                type="number"
                                min="1"
                                max="25"
                                step="1"
                                value={dailyNegotiationBudget}
                                onChange={(event) => {
                                    setDailyNegotiationBudget(
                                        event.target.value,
                                    );

                                    setErrorMessage(null);
                                }}
                            />

                            <span className="form-help">
                                W naszej konfiguracji maksymalnie 25.
                            </span>
                        </div>
                    </div>
                </article>

                <article className="content-card">
                    <div className="bot-form-section-header">
                        <div>
                            <span className="bot-form-step">
                                5
                            </span>

                            <h2 className="content-card-title">
                                Kroki negocjacji
                            </h2>
                        </div>

                        <p className="content-card-text">
                            To zrobimy jako następny element formularza.
                        </p>
                    </div>

                    <div className="negotiation-placeholder">
                        <strong>
                            Przykładowa konfiguracja
                        </strong>

                        <div>
                            Krok 1 → oferta 1500 zł
                        </div>

                        <div>
                            Krok 2 → oferta 1600 zł
                        </div>

                        <div>
                            Krok 3 → oferta 1700 zł
                        </div>
                    </div>
                </article>

                {errorMessage !== null && (
                    <div
                        className="form-message form-message-error"
                        role="alert"
                    >
                        {errorMessage}
                    </div>
                )}

                <div className="bot-form-actions">
                    <button
                        className="primary-button"
                        type="submit"
                    >
                        Sprawdź formularz
                    </button>
                </div>
            </form>
        </section>
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

export default CreateBotPage;
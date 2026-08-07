import {
    type FormEvent,
    useCallback,
    useEffect,
    useState,
} from "react";
import {
    createBrand,
    getBrands,
} from "../api/dictionariesApi";
import type {
    DictionaryBrand,
} from "../types/dictionaries";

function DictionariesPage() {
    const [brands, setBrands] =
        useState<DictionaryBrand[]>([]);

    const [brandName, setBrandName] =
        useState("");

    const [isLoading, setIsLoading] =
        useState(true);

    const [isSubmitting, setIsSubmitting] =
        useState(false);

    const [errorMessage, setErrorMessage] =
        useState<string | null>(null);

    const [successMessage, setSuccessMessage] =
        useState<string | null>(null);

    const loadBrands = useCallback(async () => {
        setIsLoading(true);
        setErrorMessage(null);

        try {
            const loadedBrands = await getBrands();

            setBrands(loadedBrands);
        } catch (error) {
            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się pobrać marek.",
                ),
            );
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadBrands();
    }, [loadBrands]);

    async function handleCreateBrand(
        event: FormEvent<HTMLFormElement>,
    ) {
        event.preventDefault();

        const normalizedName =
            brandName
                .trim()
                .replace(/\s+/g, " ");

        if (normalizedName.length === 0) {
            setErrorMessage(
                "Wpisz nazwę marki.",
            );

            return;
        }

        setIsSubmitting(true);
        setErrorMessage(null);
        setSuccessMessage(null);

        try {
            const createdBrand =
                await createBrand({
                    name: normalizedName,
                });

            setBrands((currentBrands) =>
                [...currentBrands, createdBrand]
                    .sort((firstBrand, secondBrand) =>
                        firstBrand.name.localeCompare(
                            secondBrand.name,
                            "pl",
                        ),
                    ),
            );

            setBrandName("");

            setSuccessMessage(
                `Dodano markę: ${createdBrand.name}.`,
            );
        } catch (error) {
            setErrorMessage(
                getErrorMessage(
                    error,
                    "Nie udało się dodać marki.",
                ),
            );
        } finally {
            setIsSubmitting(false);
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
                                Marki
                            </h2>

                            <p className="content-card-text">
                                Dodaj marki, które będzie można wybrać
                                podczas konfiguracji bota.
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
                                disabled={isSubmitting}
                                onChange={(event) => {
                                    setBrandName(
                                        event.target.value,
                                    );

                                    setErrorMessage(null);
                                    setSuccessMessage(null);
                                }}
                            />
                        </div>

                        <button
                            className="primary-button"
                            type="submit"
                            disabled={
                                isSubmitting
                                || brandName.trim().length === 0
                            }
                        >
                            {isSubmitting
                                ? "Dodawanie..."
                                : "Dodaj markę"}
                        </button>
                    </form>

                    {errorMessage !== null && (
                        <div
                            className="form-message form-message-error"
                            role="alert"
                        >
                            {errorMessage}
                        </div>
                    )}

                    {successMessage !== null && (
                        <div
                            className="form-message form-message-success"
                            role="status"
                        >
                            {successMessage}
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
                                Lista jest pobierana bezpośrednio
                                z backendu.
                            </p>
                        </div>

                        <button
                            className="secondary-button"
                            type="button"
                            disabled={isLoading}
                            onClick={() => {
                                void loadBrands();
                            }}
                        >
                            Odśwież
                        </button>
                    </div>

                    {isLoading ? (
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

                <article className="content-card dictionary-coming-soon">
                    <h2 className="content-card-title">
                        Kategorie
                    </h2>

                    <p className="content-card-text">
                        W kolejnym kroku dodamy formularz pełnej ścieżki
                        kategorii.
                    </p>

                    <div className="dictionary-example">
                        <span className="dictionary-example-label">
                            Przykład
                        </span>

                        <span>
                            Elektronika → Telefony komórkowe →
                            Smartfony
                        </span>
                    </div>
                </article>

                <article className="content-card dictionary-coming-soon">
                    <h2 className="content-card-title">
                        Modele
                    </h2>

                    <p className="content-card-text">
                        Model będzie zawsze dodawany do wybranej marki,
                        aby dane się nie mieszały.
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
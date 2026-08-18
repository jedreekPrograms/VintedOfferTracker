import {
    useCallback,
    useEffect,
    useState,
} from "react";

import {
    createMarketStatsObserver,
    deleteMarketStatsObserver,
    getMarketStatsObserver,
    updateMarketStatsObserver,
} from "../api/marketStatsObserverApi";

import type {
    MarketStatsObserver,
} from "../api/marketStatsObserverApi";

import "../styles/market-stats-observer.css";

interface ObserverDraft {
    name: string;
    email: string;
    password: string;
}

const EMPTY_DRAFT: ObserverDraft = {
    name: "Market Observer",
    email: "",
    password: "",
};

function MarketStatsObserverCard() {
    const [observer, setObserver] =
        useState<MarketStatsObserver | null>(null);
    const [draft, setDraft] =
        useState<ObserverDraft>(EMPTY_DRAFT);
    const [isEditing, setIsEditing] =
        useState(false);
    const [isLoading, setIsLoading] =
        useState(true);
    const [isSaving, setIsSaving] =
        useState(false);
    const [errorMessage, setErrorMessage] =
        useState<string | null>(null);

    const loadObserver = useCallback(async () => {
        setIsLoading(true);
        setErrorMessage(null);

        try {
            const loaded = await getMarketStatsObserver();
            setObserver(loaded);

            if (loaded !== null) {
                setDraft({
                    name: loaded.name,
                    email: loaded.email,
                    password: "",
                });
            }
        } catch (error) {
            setErrorMessage(getErrorMessage(error));
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadObserver();
    }, [loadObserver]);

    function beginCreate() {
        setDraft(EMPTY_DRAFT);
        setIsEditing(true);
        setErrorMessage(null);
    }

    function beginEdit() {
        if (observer === null) {
            return;
        }

        setDraft({
            name: observer.name,
            email: observer.email,
            password: "",
        });
        setIsEditing(true);
        setErrorMessage(null);
    }

    async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (isSaving) {
            return;
        }

        if (draft.name.trim().length === 0
            || draft.email.trim().length === 0) {
            setErrorMessage("Nazwa i e-mail są wymagane.");
            return;
        }

        if (observer === null && draft.password.trim().length === 0) {
            setErrorMessage("Hasło jest wymagane przy tworzeniu observera.");
            return;
        }

        setIsSaving(true);
        setErrorMessage(null);

        try {
            const saved = observer === null
                ? await createMarketStatsObserver({
                    name: draft.name.trim(),
                    email: draft.email.trim(),
                    password: draft.password,
                })
                : await updateMarketStatsObserver({
                    name: draft.name.trim(),
                    email: draft.email.trim(),
                    password: draft.password.trim().length === 0
                        ? null
                        : draft.password,
                });

            setObserver(saved);
            setDraft({
                name: saved.name,
                email: saved.email,
                password: "",
            });
            setIsEditing(false);
        } catch (error) {
            setErrorMessage(getErrorMessage(error));
        } finally {
            setIsSaving(false);
        }
    }

    async function handleDelete() {
        if (isSaving || observer === null) {
            return;
        }

        const confirmed = window.confirm(
            "Usunąć observera statystyk? Zwykłe boty nie zostaną zmienione.",
        );

        if (!confirmed) {
            return;
        }

        setIsSaving(true);
        setErrorMessage(null);

        try {
            await deleteMarketStatsObserver();
            setObserver(null);
            setDraft(EMPTY_DRAFT);
            setIsEditing(false);
        } catch (error) {
            setErrorMessage(getErrorMessage(error));
        } finally {
            setIsSaving(false);
        }
    }

    return (
        <article className="content-card market-observer-card">
            <div className="market-observer-header">
                <div>
                    <p className="market-observer-eyebrow">
                        Statystyki rynku
                    </p>
                    <h2 className="content-card-title">
                        Observer statystyk
                    </h2>
                    <p className="content-card-text">
                        Osobne konto Vinted tylko do mierzenia liczby nowych ofert.
                        Nie negocjuje i nie wysyła propozycji cenowych.
                    </p>
                </div>

                {!isLoading && observer === null && !isEditing && (
                    <button
                        className="primary-button"
                        type="button"
                        onClick={beginCreate}
                    >
                        + Dodaj observera
                    </button>
                )}
            </div>

            {errorMessage !== null && (
                <div className="form-message form-message-error" role="alert">
                    {errorMessage}
                </div>
            )}

            {isLoading ? (
                <div className="dictionary-list-state">
                    Pobieranie observera...
                </div>
            ) : isEditing ? (
                <form
                    className="market-observer-form"
                    onSubmit={(event) => void handleSubmit(event)}
                >
                    <label className="market-observer-field">
                        <span>Nazwa</span>
                        <input
                            type="text"
                            value={draft.name}
                            disabled={isSaving}
                            onChange={(event) => setDraft((current) => ({
                                ...current,
                                name: event.target.value,
                            }))}
                        />
                    </label>

                    <label className="market-observer-field">
                        <span>E-mail Vinted</span>
                        <input
                            type="email"
                            value={draft.email}
                            disabled={isSaving}
                            onChange={(event) => setDraft((current) => ({
                                ...current,
                                email: event.target.value,
                            }))}
                        />
                    </label>

                    <label className="market-observer-field">
                        <span>
                            {observer === null
                                ? "Hasło Vinted"
                                : "Nowe hasło (opcjonalnie)"}
                        </span>
                        <input
                            type="password"
                            value={draft.password}
                            disabled={isSaving}
                            autoComplete="new-password"
                            onChange={(event) => setDraft((current) => ({
                                ...current,
                                password: event.target.value,
                            }))}
                        />
                    </label>

                    <div className="market-observer-form-actions">
                        <button
                            className="primary-button"
                            type="submit"
                            disabled={isSaving}
                        >
                            {isSaving ? "Zapisywanie..." : "Zapisz observera"}
                        </button>
                        <button
                            className="secondary-button"
                            type="button"
                            disabled={isSaving}
                            onClick={() => setIsEditing(false)}
                        >
                            Anuluj
                        </button>
                    </div>
                </form>
            ) : observer !== null ? (
                <div className="market-observer-summary">
                    <div className="market-observer-identity">
                        <strong>{observer.name}</strong>
                        <span>{observer.email}</span>
                    </div>

                    <div className="market-observer-runtime">
                        <span className="market-observer-status-dot" />
                        <div>
                            <strong>Automatyczny collector</strong>
                            <span>
                                Uruchamia się niezależnie od zwykłych botów razem z Playwrightem.
                            </span>
                        </div>
                    </div>

                    <span className="bot-id">#{observer.id}</span>

                    <div className="market-observer-actions">
                        <button
                            className="secondary-button"
                            type="button"
                            disabled={isSaving}
                            onClick={beginEdit}
                        >
                            Edytuj
                        </button>
                        <button
                            className="bot-stop-button"
                            type="button"
                            disabled={isSaving}
                            onClick={() => void handleDelete()}
                        >
                            Usuń
                        </button>
                    </div>
                </div>
            ) : (
                <div className="market-observer-empty">
                    <strong>Brak observera</strong>
                    <span>
                        Dodaj jedno techniczne konto Vinted. Nie potrzebuje kategorii,
                        modelu ani kroków negocjacji.
                    </span>
                </div>
            )}
        </article>
    );
}

function getErrorMessage(error: unknown): string {
    return error instanceof Error
        ? error.message
        : "Wystąpił nieoczekiwany błąd observera.";
}

export default MarketStatsObserverCard;

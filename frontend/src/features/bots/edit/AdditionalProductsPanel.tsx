import {
    useEffect,
    useState,
} from "react";
import {
    Link,
    useParams,
} from "react-router-dom";

import { getBot } from "../../../api/botsApi";
import { deactivateAdditionalTarget } from "../../../api/additionalTargetsApi";
import type {
    BotAdditionalTargetDetails,
    BotDetails,
} from "../../../types/bots";

const MAX_ADDITIONAL_PRODUCTS = 4;

function AdditionalProductsPanel() {
    const { botId: botIdParam } = useParams<{ botId: string }>();
    const botId = Number(botIdParam);
    const [bot, setBot] = useState<BotDetails | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isDeleting, setIsDeleting] = useState<number | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    async function reload() {
        if (!Number.isInteger(botId) || botId <= 0) {
            setErrorMessage("Nieprawidłowy identyfikator bota.");
            setIsLoading(false);
            return;
        }

        setIsLoading(true);
        try {
            setBot(await getBot(botId));
            setErrorMessage(null);
        } catch (error) {
            setErrorMessage(getErrorMessage(
                error,
                "Nie udało się pobrać dodatkowych produktów.",
            ));
        } finally {
            setIsLoading(false);
        }
    }

    useEffect(() => {
        void reload();
    }, [botId]);

    async function handleDeactivate(target: BotAdditionalTargetDetails) {
        if (bot === null || bot.status.toUpperCase() !== "STOPPED") {
            return;
        }

        const label = productLabel(target);
        if (!window.confirm(
            `Wyłączyć dodatkowy produkt „${label}”? Nowe ogłoszenia nie będą już skanowane, ale rozpoczęte rozmowy zachowają jego strategię.`,
        )) {
            return;
        }

        setIsDeleting(target.additionalTargetId);
        setErrorMessage(null);
        try {
            await deactivateAdditionalTarget(botId, target.additionalTargetId);
            await reload();
        } catch (error) {
            setErrorMessage(getErrorMessage(
                error,
                "Nie udało się wyłączyć dodatkowego produktu.",
            ));
        } finally {
            setIsDeleting(null);
        }
    }

    if (isLoading) {
        return (
            <section className="page">
                <article className="content-card">
                    <div className="dictionary-list-state">
                        Pobieranie produktów bota...
                    </div>
                </article>
            </section>
        );
    }

    if (bot === null) {
        return errorMessage === null ? null : (
            <section className="page">
                <div className="form-message form-message-error" role="alert">
                    {errorMessage}
                </div>
            </section>
        );
    }

    const stopped = bot.status.toUpperCase() === "STOPPED";
    const additionalTargets = bot.additionalTargets ?? [];
    const canAdd = stopped && additionalTargets.length < MAX_ADDITIONAL_PRODUCTS;

    return (
        <section className="page">
            <article className="content-card">
                <div className="bot-form-section-header">
                    <div>
                        <span className="bot-form-step">+</span>
                        <h2 className="content-card-title">Dodatkowe produkty</h2>
                    </div>
                    <p className="content-card-text">
                        To samo konto Vinted może obsługiwać maksymalnie 5 produktów:
                        obecny produkt główny i do 4 dodatkowych. Konto, sesja i dzienny
                        budżet pozostają wspólne; filtry i strategia negocjacji są osobne.
                    </p>
                </div>

                {errorMessage !== null && (
                    <div className="form-message form-message-error" role="alert">
                        {errorMessage}
                    </div>
                )}

                <div className="information-box">
                    <strong>Produkt główny:</strong>{" "}
                    {bot.configuration.brand} → {targetName(bot.configuration)} · {formatPrice(bot.configuration.minPrice)}–{formatPrice(bot.configuration.maxPrice)} zł
                    <div className="form-help">
                        To jest dotychczasowa konfiguracja bota. Nie jest przenoszona ani zmieniana przez tę funkcję.
                    </div>
                </div>

                {additionalTargets.length === 0 ? (
                    <div className="dictionary-list-state">
                        Ten bot nie ma dodatkowych produktów. Działa dokładnie jak wcześniej.
                    </div>
                ) : (
                    <div className="dictionary-list">
                        {additionalTargets.map((target) => (
                            <div
                                className="dictionary-list-item"
                                key={target.additionalTargetId}
                            >
                                <div>
                                    <strong>{productLabel(target)}</strong>
                                    <div className="form-help">
                                        {target.categoryPath.join(" → ")} · {formatPrice(target.minPrice)}–{formatPrice(target.maxPrice)} zł · {target.negotiationSteps.length} kroków
                                    </div>
                                </div>
                                <div className="bot-form-actions">
                                    <Link
                                        className="secondary-button"
                                        to={`/bots/${botId}/additional-products/${target.additionalTargetId}/edit`}
                                    >
                                        Edytuj
                                    </Link>
                                    <button
                                        className="secondary-button"
                                        type="button"
                                        disabled={!stopped || isDeleting !== null}
                                        onClick={() => void handleDeactivate(target)}
                                    >
                                        {isDeleting === target.additionalTargetId
                                            ? "Wyłączanie..."
                                            : "Wyłącz"}
                                    </button>
                                </div>
                            </div>
                        ))}
                    </div>
                )}

                {!stopped && (
                    <div className="information-box">
                        Zatrzymaj bota, aby dodawać, edytować lub wyłączać dodatkowe produkty.
                    </div>
                )}

                <div className="bot-form-actions">
                    {canAdd ? (
                        <Link
                            className="primary-button"
                            to={`/bots/${botId}/additional-products/new`}
                        >
                            + Dodaj produkt
                        </Link>
                    ) : (
                        <button className="primary-button" type="button" disabled>
                            {additionalTargets.length >= MAX_ADDITIONAL_PRODUCTS
                                ? "Limit 4 dodatkowych produktów"
                                : "+ Dodaj produkt"}
                        </button>
                    )}
                    <span className="form-help">
                        {additionalTargets.length} / {MAX_ADDITIONAL_PRODUCTS} dodatkowych · {additionalTargets.length + 1} / 5 łącznie
                    </span>
                </div>
            </article>
        </section>
    );
}

function productLabel(target: BotAdditionalTargetDetails): string {
    return `${target.brand} → ${targetName(target)}`;
}

function targetName(target: {
    targetMode: string | null;
    model: string | null;
    searchQuery: string | null;
}): string {
    return target.targetMode === "SEARCH_QUERY"
        ? target.searchQuery ?? "wyszukiwanie"
        : target.model ?? "model";
}

function formatPrice(value: number): string {
    return new Intl.NumberFormat("pl-PL", {
        maximumFractionDigits: 2,
    }).format(value);
}

function getErrorMessage(error: unknown, fallback: string): string {
    return error instanceof Error ? error.message : fallback;
}

export default AdditionalProductsPanel;

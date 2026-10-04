import {
    useEffect,
    useState,
} from "react";

import {
    getMarketStatsHealth,
    type MarketStatsHealth,
    type MarketStatsHealthStatus,
} from "../api/marketStatsApi";
import "../styles/market-stats-observer.css";

const HEALTH_REFRESH_MS = 60_000;

function MarketStatsObserverCard() {
    const [health, setHealth] = useState<MarketStatsHealth | null>(null);
    const [healthError, setHealthError] = useState(false);

    useEffect(() => {
        let cancelled = false;

        async function loadHealth() {
            try {
                const next = await getMarketStatsHealth();
                if (!cancelled) {
                    setHealth(next);
                    setHealthError(false);
                }
            } catch {
                if (!cancelled) {
                    setHealthError(true);
                }
            }
        }

        void loadHealth();
        const intervalId = window.setInterval(
            () => void loadHealth(),
            HEALTH_REFRESH_MS,
        );

        return () => {
            cancelled = true;
            window.clearInterval(intervalId);
        };
    }, []);

    const status = healthError
        ? observerStatusCopy("STALE")
        : observerStatusCopy(health?.status ?? "WAITING");

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
                        Anonimowy, tylko do odczytu collector publicznego katalogu Vinted.
                        Nie wymaga konta, e-maila ani hasła i nigdy nie negocjuje.
                    </p>
                </div>
            </div>

            <div className="market-observer-summary market-observer-summary-system">
                <div className="market-observer-identity">
                    <strong>Anonymous Market Observer</strong>
                    <span>Bez konta Vinted i bez zapisanej sesji użytkownika</span>
                </div>

                <div className="market-observer-runtime">
                    <span
                        className={`market-observer-status-dot market-observer-status-${status.tone}`}
                        aria-hidden="true"
                    />
                    <div>
                        <strong>{status.title}</strong>
                        <span>{status.description}</span>
                        {health !== null && (
                            <span className="market-observer-health-detail">
                                {formatHealthDetail(health)}
                            </span>
                        )}
                    </div>
                </div>

                <span className="market-observer-system-badge">
                    READ ONLY
                </span>
            </div>
        </article>
    );
}

function observerStatusCopy(status: MarketStatsHealthStatus): {
    tone: "ok" | "warning" | "muted" | "danger";
    title: string;
    description: string;
} {
    switch (status) {
        case "OK":
            return {
                tone: "ok",
                title: "Observer działa prawidłowo",
                description: "Ostatnie skany są kompletne i świeże.",
            };
        case "PARTIAL":
            return {
                tone: "warning",
                title: "Ostatni skan był niepełny",
                description: "Dane są zachowane, ale Observer spróbuje uzupełnić brakujące pokrycie.",
            };
        case "WARMING_UP":
            return {
                tone: "warning",
                title: "Observer buduje baseline",
                description: "Część modeli nie ma jeszcze pełnego punktu startowego.",
            };
        case "STALE":
            return {
                tone: "danger",
                title: "Observer jest opóźniony",
                description: "Brakuje świeżego udanego skanu. Sprawdź Playwright lub obciążenie hosta.",
            };
        case "IDLE":
            return {
                tone: "muted",
                title: "Brak modeli do obserwacji",
                description: "Observer zacznie pracę po dodaniu modelu do słownika.",
            };
        case "WAITING":
        default:
            return {
                tone: "muted",
                title: "Observer czeka na pierwszy skan",
                description: "Collector jest skonfigurowany, ale backend nie ma jeszcze danych o wykonanym skanie.",
            };
    }
}

function formatHealthDetail(health: MarketStatsHealth): string {
    const parts = [
        `${health.baselineReadyModels}/${health.totalModels} modeli z baseline`,
    ];

    if (health.incompleteModels > 0) {
        parts.push(`${health.incompleteModels} niepełnych`);
    }

    if (health.staleModels > 0) {
        parts.push(`${health.staleModels} opóźnionych`);
    }

    if (health.lastSuccessfulScanAt !== null) {
        parts.push(
            `najnowszy udany: ${formatDateTime(health.lastSuccessfulScanAt)}`,
        );

        if (
            health.oldestSuccessfulScanAt !== null
            && health.oldestSuccessfulScanAt !== health.lastSuccessfulScanAt
        ) {
            parts.push(
                `najstarszy model: ${formatDateTime(health.oldestSuccessfulScanAt)}`,
            );
        }
    } else if (health.lastScanAt !== null) {
        parts.push(
            `ostatnia próba: ${formatDateTime(health.lastScanAt)}`,
        );
    }

    return parts.join(" · ");
}

function formatDateTime(value: string): string {
    const parsed = new Date(value);

    if (Number.isNaN(parsed.getTime())) {
        return value;
    }

    return new Intl.DateTimeFormat("pl-PL", {
        dateStyle: "short",
        timeStyle: "short",
    }).format(parsed);
}

export default MarketStatsObserverCard;

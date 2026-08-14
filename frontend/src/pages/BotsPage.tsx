import {
    useCallback,
    useEffect,
    useState,
} from "react";
import { Link } from "react-router-dom";

import {
    getBotOfferQuota,
    getBots,
    startBot,
    stopBot,
} from "../api/botsApi";
import type { BotOfferQuota } from "../api/botsApi";
import MarketStatsObserverCard from "../components/MarketStatsObserverCard";
import type { BotListItem } from "../types/bots";

function BotsPage() {
    const [bots, setBots] = useState<BotListItem[]>([]);
    const [quotaByBotId, setQuotaByBotId] =
        useState<Record<number, BotOfferQuota>>({});
    const [isLoading, setIsLoading] = useState(true);
    const [actionBotId, setActionBotId] = useState<number | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const loadBots = useCallback(async () => {
        setIsLoading(true);
        setErrorMessage(null);

        try {
            const loadedBots = await getBots();
            setBots(loadedBots);

            const quotaResults = await Promise.all(
                loadedBots.map(async (bot) => {
                    try {
                        return {
                            botId: bot.id,
                            quota: await getBotOfferQuota(bot.id),
                        };
                    } catch (error) {
                        console.error(
                            `Nie udało się pobrać quota dla bota ${bot.id}.`,
                            error,
                        );
                        return null;
                    }
                }),
            );

            const nextQuotaByBotId: Record<number, BotOfferQuota> = {};

            for (const result of quotaResults) {
                if (result !== null) {
                    nextQuotaByBotId[result.botId] = result.quota;
                }
            }

            setQuotaByBotId(nextQuotaByBotId);
        } catch (error) {
            setErrorMessage(
                getErrorMessage(error, "Nie udało się pobrać botów."),
            );
        } finally {
            setIsLoading(false);
        }
    }, []);

    useEffect(() => {
        void loadBots();
    }, [loadBots]);

    async function handleStartBot(botId: number) {
        if (actionBotId !== null) {
            return;
        }

        setActionBotId(botId);
        setErrorMessage(null);

        try {
            await startBot(botId);
            await loadBots();
        } catch (error) {
            setErrorMessage(
                getErrorMessage(error, "Nie udało się uruchomić bota."),
            );
        } finally {
            setActionBotId(null);
        }
    }

    async function handleStopBot(botId: number) {
        if (actionBotId !== null) {
            return;
        }

        setActionBotId(botId);
        setErrorMessage(null);

        try {
            await stopBot(botId);
            await loadBots();
        } catch (error) {
            setErrorMessage(
                getErrorMessage(error, "Nie udało się zatrzymać bota."),
            );
        } finally {
            setActionBotId(null);
        }
    }

    return (
        <section className="page">
            <header className="page-header bots-page-header">
                <div>
                    <p className="page-eyebrow">Zarządzanie</p>
                    <h1 className="page-title">Boty</h1>
                    <p className="page-description">
                        Zarządzaj botami, ich aktualnym stanem,
                        konfiguracją oraz dziennym limitem ofert.
                    </p>
                </div>

                <Link className="primary-button" to="/bots/create">
                    + Utwórz bota
                </Link>
            </header>

            {errorMessage !== null && (
                <div className="form-message form-message-error" role="alert">
                    {errorMessage}
                </div>
            )}

            <MarketStatsObserverCard />

            <article className="content-card">
                <div className="bots-list-header">
                    <div>
                        <h2 className="content-card-title">
                            Wszystkie boty
                        </h2>
                        <p className="content-card-text">
                            Zwykłe boty negocjacyjne zapisane obecnie w backendzie.
                        </p>
                    </div>

                    <div className="bots-list-header-actions">
                        <span className="dictionary-count">{bots.length}</span>
                        <button
                            className="secondary-button"
                            type="button"
                            disabled={isLoading}
                            onClick={() => void loadBots()}
                        >
                            {isLoading ? "Odświeżanie..." : "Odśwież"}
                        </button>
                    </div>
                </div>

                {isLoading ? (
                    <div className="dictionary-list-state">
                        Pobieranie botów...
                    </div>
                ) : bots.length === 0 ? (
                    <div className="bots-empty-state">
                        <h3>Nie masz jeszcze żadnego bota</h3>
                        <p>
                            Utwórz pierwszego bota i skonfiguruj konto Vinted,
                            filtry oraz strategię negocjacji.
                        </p>
                        <Link className="primary-button" to="/bots/create">
                            Utwórz pierwszego bota
                        </Link>
                    </div>
                ) : (
                    <div className="bots-table-wrapper">
                        <table className="bots-table">
                            <thead>
                                <tr>
                                    <th>Bot</th>
                                    <th>Konto</th>
                                    <th>Status</th>
                                    <th>Dzisiejsze oferty</th>
                                    <th>ID</th>
                                    <th className="bots-actions-column">
                                        Akcje
                                    </th>
                                </tr>
                            </thead>
                            <tbody>
                                {bots.map((bot) => {
                                    const isRunning =
                                        bot.status.toUpperCase() === "RUNNING";
                                    const isActionInProgress =
                                        actionBotId === bot.id;
                                    const quota = quotaByBotId[bot.id];

                                    return (
                                        <tr key={bot.id}>
                                            <td>
                                                <div className="bot-name-cell">
                                                    <strong>{bot.name}</strong>
                                                    <span>Vinted</span>
                                                </div>
                                            </td>
                                            <td>{bot.email}</td>
                                            <td>
                                                <BotStatus status={bot.status} />
                                            </td>
                                            <td>
                                                {quota ? (
                                                    <BotOfferQuotaCell quota={quota} />
                                                ) : (
                                                    <span>Brak danych</span>
                                                )}
                                            </td>
                                            <td>
                                                <span className="bot-id">
                                                    #{bot.id}
                                                </span>
                                            </td>
                                            <td>
                                                <div className="bot-row-actions">
                                                    {isRunning ? (
                                                        <button
                                                            className="bot-stop-button"
                                                            type="button"
                                                            disabled={isActionInProgress}
                                                            onClick={() => void handleStopBot(bot.id)}
                                                        >
                                                            {isActionInProgress
                                                                ? "Zatrzymywanie..."
                                                                : "Zatrzymaj"}
                                                        </button>
                                                    ) : (
                                                        <>
                                                            <button
                                                                className="bot-start-button"
                                                                type="button"
                                                                disabled={isActionInProgress}
                                                                onClick={() => void handleStartBot(bot.id)}
                                                            >
                                                                {isActionInProgress
                                                                    ? "Uruchamianie..."
                                                                    : "Uruchom"}
                                                            </button>
                                                            <Link
                                                                className="secondary-button"
                                                                to={`/bots/${bot.id}/edit`}
                                                            >
                                                                Edytuj
                                                            </Link>
                                                        </>
                                                    )}
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}
            </article>
        </section>
    );
}

interface BotOfferQuotaCellProps {
    quota: BotOfferQuota;
}

function BotOfferQuotaCell({ quota }: BotOfferQuotaCellProps) {
    const safeLimit = Math.max(quota.limit, 1);
    const safeUsed = Math.min(Math.max(quota.used, 0), safeLimit);
    const percentage = Math.round((safeUsed / safeLimit) * 100);

    return (
        <div className="bot-quota-cell">
            <div className="bot-quota-header">
                <strong className="bot-quota-value">
                    {quota.used} / {quota.limit}
                </strong>
                <span className="bot-quota-percentage">
                    {percentage}%
                </span>
            </div>

            <progress
                className="bot-quota-progress"
                max={safeLimit}
                value={safeUsed}
                aria-label={`Wykorzystano ${quota.used} z ${quota.limit} ofert`}
            />

            <span className="bot-quota-remaining">
                Pozostało: <strong>{quota.remaining}</strong>
            </span>
        </div>
    );
}

interface BotStatusProps {
    status: string;
}

function BotStatus({ status }: BotStatusProps) {
    const normalizedStatus = status.toUpperCase();
    const statusClassName = normalizedStatus === "RUNNING"
        ? "bot-status bot-status-running"
        : "bot-status bot-status-stopped";

    return (
        <span className={statusClassName}>
            <span className="bot-status-dot" />
            {formatBotStatus(status)}
        </span>
    );
}

function formatBotStatus(status: string): string {
    switch (status.toUpperCase()) {
        case "RUNNING":
            return "Uruchomiony";
        case "STOPPED":
            return "Zatrzymany";
        default:
            return status;
    }
}

function getErrorMessage(
    error: unknown,
    fallbackMessage: string,
): string {
    return error instanceof Error
        ? error.message
        : fallbackMessage;
}

export default BotsPage;

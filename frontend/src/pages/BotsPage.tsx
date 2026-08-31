import {
    useCallback,
    useEffect,
    useMemo,
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
import AppDialog from "../components/AppDialog";
import MarketStatsObserverCard from "../components/MarketStatsObserverCard";
import type { BotListItem } from "../types/bots";

type BulkAction = "START" | "STOP";
type LoadMode = "initial" | "background";

function BotsPage() {
    const [bots, setBots] = useState<BotListItem[]>([]);
    const [quotaByBotId, setQuotaByBotId] =
        useState<Record<number, BotOfferQuota>>({});
    const [isInitialLoading, setIsInitialLoading] = useState(true);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [actionBotId, setActionBotId] = useState<number | null>(null);
    const [bulkAction, setBulkAction] = useState<BulkAction | null>(null);
    const [pendingBulkAction, setPendingBulkAction] = useState<BulkAction | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [successMessage, setSuccessMessage] = useState<string | null>(null);

    const loadBots = useCallback(async (mode: LoadMode) => {
        if (mode === "initial") {
            setIsInitialLoading(true);
        } else {
            setIsRefreshing(true);
        }
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
                        console.error(`Nie udało się pobrać quota dla bota ${bot.id}.`, error);
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
            setErrorMessage(getErrorMessage(error, "Nie udało się pobrać botów."));
        } finally {
            if (mode === "initial") {
                setIsInitialLoading(false);
            } else {
                setIsRefreshing(false);
            }
        }
    }, []);

    useEffect(() => {
        void loadBots("initial");
    }, [loadBots]);

    const runningBots = useMemo(
        () => bots.filter((bot) => bot.status.toUpperCase() === "RUNNING"),
        [bots],
    );
    const stoppedBots = useMemo(
        () => bots.filter((bot) => bot.status.toUpperCase() !== "RUNNING"),
        [bots],
    );

    const anyActionBusy = actionBotId !== null || bulkAction !== null;

    async function handleStartBot(botId: number) {
        if (anyActionBusy) {
            return;
        }

        setActionBotId(botId);
        setErrorMessage(null);
        setSuccessMessage(null);

        try {
            await startBot(botId);
            await loadBots("background");
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Nie udało się uruchomić bota."));
        } finally {
            setActionBotId(null);
        }
    }

    async function handleStopBot(botId: number) {
        if (anyActionBusy) {
            return;
        }

        setActionBotId(botId);
        setErrorMessage(null);
        setSuccessMessage(null);

        try {
            await stopBot(botId);
            await loadBots("background");
        } catch (error) {
            setErrorMessage(getErrorMessage(error, "Nie udało się zatrzymać bota."));
        } finally {
            setActionBotId(null);
        }
    }

    async function runBulkAction(action: BulkAction) {
        if (anyActionBusy) {
            return;
        }

        const targets = action === "START" ? stoppedBots : runningBots;
        setPendingBulkAction(null);

        if (targets.length === 0) {
            return;
        }

        setBulkAction(action);
        setErrorMessage(null);
        setSuccessMessage(null);

        try {
            const results = await Promise.allSettled(
                targets.map((bot) => action === "START"
                    ? startBot(bot.id)
                    : stopBot(bot.id)),
            );

            const failedBots = results
                .map((result, index) => result.status === "rejected" ? targets[index] : null)
                .filter((bot): bot is BotListItem => bot !== null);

            await loadBots("background");

            const completedCount = targets.length - failedBots.length;
            const actionLabel = action === "START" ? "uruchomiono" : "zatrzymano";

            if (failedBots.length === 0) {
                setSuccessMessage(
                    `${action === "START" ? "Uruchomiono" : "Zatrzymano"} wszystkie boty (${completedCount}).`,
                );
            } else {
                setErrorMessage(
                    `${action === "START" ? "Nie udało się uruchomić" : "Nie udało się zatrzymać"} ${failedBots.length} z ${targets.length} botów. `
                    + `Poprawnie ${actionLabel}: ${completedCount}. Problem: ${failedBots.map((bot) => bot.name).join(", ")}.`,
                );
            }
        } finally {
            setBulkAction(null);
        }
    }

    const pendingTargets = pendingBulkAction === "START" ? stoppedBots : runningBots;

    return (
        <section className="page">
            <header className="page-header bots-page-header">
                <div>
                    <p className="page-eyebrow">Zarządzanie</p>
                    <h1 className="page-title">Boty</h1>
                    <p className="page-description">
                        Zarządzaj botami, ich aktualnym stanem, konfiguracją oraz dziennym limitem ofert.
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
            {successMessage !== null && (
                <div className="form-message form-message-success" role="status">
                    {successMessage}
                </div>
            )}

            <MarketStatsObserverCard />

            <article className="content-card">
                <div className="bots-list-header">
                    <div>
                        <h2 className="content-card-title">Wszystkie boty</h2>
                        <p className="content-card-text">
                            Zwykłe boty negocjacyjne zapisane obecnie w backendzie.
                        </p>
                    </div>

                    <div className="bots-list-header-actions">
                        <span className="dictionary-count">{bots.length}</span>
                        <button
                            className="bot-start-button"
                            type="button"
                            disabled={anyActionBusy || stoppedBots.length === 0 || isInitialLoading}
                            onClick={() => setPendingBulkAction("START")}
                        >
                            {bulkAction === "START"
                                ? "Uruchamianie..."
                                : `Uruchom wszystkie (${stoppedBots.length})`}
                        </button>
                        <button
                            className="bot-stop-button"
                            type="button"
                            disabled={anyActionBusy || runningBots.length === 0 || isInitialLoading}
                            onClick={() => setPendingBulkAction("STOP")}
                        >
                            {bulkAction === "STOP"
                                ? "Zatrzymywanie..."
                                : `Zatrzymaj wszystkie (${runningBots.length})`}
                        </button>
                        <button
                            className="secondary-button"
                            type="button"
                            disabled={isInitialLoading || isRefreshing || anyActionBusy}
                            onClick={() => void loadBots("background")}
                        >
                            {isRefreshing ? "Odświeżanie..." : "Odśwież"}
                        </button>
                    </div>
                </div>

                {isInitialLoading && bots.length === 0 ? (
                    <div className="dictionary-list-state">Pobieranie botów...</div>
                ) : bots.length === 0 ? (
                    <div className="bots-empty-state">
                        <h3>Nie masz jeszcze żadnego bota</h3>
                        <p>
                            Utwórz pierwszego bota i skonfiguruj konto Vinted, filtry oraz strategię negocjacji.
                        </p>
                        <Link className="primary-button" to="/bots/create">
                            Utwórz pierwszego bota
                        </Link>
                    </div>
                ) : (
                    <div className="bots-table-wrapper" aria-busy={isRefreshing || anyActionBusy}>
                        <table className="bots-table">
                            <thead>
                                <tr>
                                    <th>Bot</th>
                                    <th>Konto</th>
                                    <th>Status</th>
                                    <th>Dzisiejsze oferty</th>
                                    <th>ID</th>
                                    <th className="bots-actions-column">Akcje</th>
                                </tr>
                            </thead>
                            <tbody>
                                {bots.map((bot) => {
                                    const isRunning = bot.status.toUpperCase() === "RUNNING";
                                    const isActionInProgress = actionBotId === bot.id;
                                    const quota = quotaByBotId[bot.id];

                                    return (
                                        <tr key={bot.id}>
                                            <td data-label="Bot">
                                                <div className="bot-name-cell">
                                                    <strong>{bot.name}</strong>
                                                    <span>Vinted</span>
                                                </div>
                                            </td>
                                            <td data-label="Konto" className="bots-email-cell">
                                                {bot.email}
                                            </td>
                                            <td data-label="Status">
                                                <BotStatus status={bot.status} />
                                            </td>
                                            <td data-label="Dzisiejsze oferty">
                                                {quota ? <BotOfferQuotaCell quota={quota} /> : <span>Brak danych</span>}
                                            </td>
                                            <td data-label="ID">
                                                <span className="bot-id">#{bot.id}</span>
                                            </td>
                                            <td data-label="Akcje" className="bots-actions-cell">
                                                <div className="bot-row-actions">
                                                    {isRunning ? (
                                                        <button
                                                            className="bot-stop-button"
                                                            type="button"
                                                            disabled={anyActionBusy}
                                                            onClick={() => void handleStopBot(bot.id)}
                                                        >
                                                            {isActionInProgress ? "Zatrzymywanie..." : "Zatrzymaj"}
                                                        </button>
                                                    ) : (
                                                        <>
                                                            <button
                                                                className="bot-start-button"
                                                                type="button"
                                                                disabled={anyActionBusy}
                                                                onClick={() => void handleStartBot(bot.id)}
                                                            >
                                                                {isActionInProgress ? "Uruchamianie..." : "Uruchom"}
                                                            </button>
                                                            <Link className="secondary-button" to={`/bots/${bot.id}/edit`}>
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

            <AppDialog
                open={pendingBulkAction !== null}
                title={pendingBulkAction === "START" ? "Uruchomić wszystkie boty?" : "Zatrzymać wszystkie boty?"}
                description={
                    pendingBulkAction === "START"
                        ? `Uruchomione zostaną ${pendingTargets.length} aktualnie zatrzymane boty. Boty już działające pozostaną bez zmian.`
                        : `Zatrzymane zostaną ${pendingTargets.length} aktualnie działające boty. Zapisane negocjacje pozostaną w systemie.`
                }
                confirmLabel={pendingBulkAction === "START" ? "Uruchom wszystkie" : "Zatrzymaj wszystkie"}
                danger={pendingBulkAction === "STOP"}
                busy={bulkAction !== null}
                onCancel={() => setPendingBulkAction(null)}
                onConfirm={() => {
                    if (pendingBulkAction !== null) {
                        void runBulkAction(pendingBulkAction);
                    }
                }}
            />
        </section>
    );
}

function BotOfferQuotaCell({ quota }: { quota: BotOfferQuota }) {
    const safeLimit = Math.max(quota.limit, 1);
    const safeUsed = Math.min(Math.max(quota.used, 0), safeLimit);
    const percentage = Math.round((safeUsed / safeLimit) * 100);

    return (
        <div className="bot-quota-cell">
            <div className="bot-quota-header">
                <strong className="bot-quota-value">{quota.used} / {quota.limit}</strong>
                <span className="bot-quota-percentage">{percentage}%</span>
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

function BotStatus({ status }: { status: string }) {
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

function getErrorMessage(error: unknown, fallbackMessage: string): string {
    return error instanceof Error ? error.message : fallbackMessage;
}

export default BotsPage;

import {
    useCallback,
    useEffect,
    useMemo,
    useState,
} from "react";
import { Link } from "react-router-dom";

import {
    getBotDailyActivity,
    getBotRuntimeState,
    getBots,
    startBot,
    stopBot,
} from "../api/botsApi";
import type {
    BotDailyActivity,
    BotRuntimeState,
} from "../api/botsApi";
import AppDialog from "../components/AppDialog";
import MarketStatsObserverCard from "../components/MarketStatsObserverCard";
import type { BotListItem } from "../types/bots";

type BulkAction = "START" | "STOP";
type LoadMode = "initial" | "background";

function BotsPage() {
    const [bots, setBots] = useState<BotListItem[]>([]);
    const [activityByBotId, setActivityByBotId] =
        useState<Record<number, BotDailyActivity>>({});
    const [runtimeByBotId, setRuntimeByBotId] =
        useState<Record<number, BotRuntimeState>>({});
    const [nowMs, setNowMs] = useState(() => Date.now());
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

            const supplementaryResults = await Promise.all(
                loadedBots.map(async (bot) => {
                    const [activityResult, runtimeResult] = await Promise.allSettled([
                        getBotDailyActivity(bot.id),
                        getBotRuntimeState(bot.id),
                    ]);

                    if (activityResult.status === "rejected") {
                        console.error(
                            `Nie udało się pobrać dzisiejszej aktywności bota ${bot.id}.`,
                            activityResult.reason,
                        );
                    }
                    if (runtimeResult.status === "rejected") {
                        console.error(`Nie udało się pobrać runtime bota ${bot.id}.`, runtimeResult.reason);
                    }

                    return {
                        botId: bot.id,
                        activity: activityResult.status === "fulfilled" ? activityResult.value : null,
                        runtime: runtimeResult.status === "fulfilled" ? runtimeResult.value : null,
                    };
                }),
            );

            const nextActivityByBotId: Record<number, BotDailyActivity> = {};
            const nextRuntimeByBotId: Record<number, BotRuntimeState> = {};

            for (const result of supplementaryResults) {
                if (result.activity !== null) {
                    nextActivityByBotId[result.botId] = result.activity;
                }
                if (result.runtime !== null) {
                    nextRuntimeByBotId[result.botId] = result.runtime;
                }
            }

            setActivityByBotId(nextActivityByBotId);
            setRuntimeByBotId(nextRuntimeByBotId);
            setNowMs(Date.now());
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

    useEffect(() => {
        const timer = window.setInterval(() => setNowMs(Date.now()), 15_000);
        return () => window.clearInterval(timer);
    }, []);

    useEffect(() => {
        if (bots.length === 0) {
            return;
        }

        const refreshSupplementaryData = async () => {
            const results = await Promise.all(
                bots.map(async (bot) => {
                    const [activityResult, runtimeResult] = await Promise.allSettled([
                        getBotDailyActivity(bot.id),
                        getBotRuntimeState(bot.id),
                    ]);

                    if (activityResult.status === "rejected") {
                        console.error(
                            `Nie udało się odświeżyć dzisiejszej aktywności bota ${bot.id}.`,
                            activityResult.reason,
                        );
                    }
                    if (runtimeResult.status === "rejected") {
                        console.error(
                            `Nie udało się odświeżyć runtime bota ${bot.id}.`,
                            runtimeResult.reason,
                        );
                    }

                    return {
                        botId: bot.id,
                        activity: activityResult.status === "fulfilled" ? activityResult.value : null,
                        runtime: runtimeResult.status === "fulfilled" ? runtimeResult.value : null,
                    };
                }),
            );

            setActivityByBotId((previous) => {
                const next = { ...previous };
                for (const result of results) {
                    if (result.activity !== null) {
                        next[result.botId] = result.activity;
                    }
                }
                return next;
            });

            setRuntimeByBotId((previous) => {
                const next = { ...previous };
                for (const result of results) {
                    if (result.runtime !== null) {
                        next[result.botId] = result.runtime;
                    }
                }
                return next;
            });
            setNowMs(Date.now());
        };

        const timer = window.setInterval(() => {
            void refreshSupplementaryData();
        }, 5_000);

        return () => window.clearInterval(timer);
    }, [bots]);

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
                        Zarządzaj botami, ich aktualnym stanem, negocjacjami oraz dziennym limitem realnych akcji ofertowych.
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
                            Statystyki dzienne są liczone od 00:00 w strefie Europe/Warsaw i odświeżają się automatycznie.
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
                                    <th>Limit dzienny</th>
                                    <th>Aktywne negocjacje</th>
                                    <th>Nowe dziś</th>
                                    <th>Kroki dziś</th>
                                    <th>ID</th>
                                    <th className="bots-actions-column">Akcje</th>
                                </tr>
                            </thead>
                            <tbody>
                                {bots.map((bot) => {
                                    const isRunning = bot.status.toUpperCase() === "RUNNING";
                                    const isActionInProgress = actionBotId === bot.id;
                                    const activity = activityByBotId[bot.id];
                                    const runtime = runtimeByBotId[bot.id];

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
                                                <BotStatus
                                                    status={bot.status}
                                                    runtime={runtime}
                                                    nowMs={nowMs}
                                                />
                                            </td>
                                            <td data-label="Limit dzienny">
                                                {activity
                                                    ? <BotDailyLimitCell activity={activity} />
                                                    : <span>Brak danych</span>}
                                            </td>
                                            <td data-label="Aktywne negocjacje">
                                                <strong>{activity?.activeNegotiations ?? "—"}</strong>
                                            </td>
                                            <td data-label="Nowe dziś">
                                                <strong>{activity?.newNegotiationsToday ?? "—"}</strong>
                                            </td>
                                            <td data-label="Kroki dziś">
                                                {activity
                                                    ? <BotNegotiationStepsCell activity={activity} />
                                                    : <span>Brak danych</span>}
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

function BotDailyLimitCell({ activity }: { activity: BotDailyActivity }) {
    const safeLimit = Math.max(activity.dailyLimit, 1);
    const safeUsed = Math.min(Math.max(activity.dailyLimitUsed, 0), safeLimit);
    const percentage = activity.dailyLimit > 0
        ? Math.round((safeUsed / safeLimit) * 100)
        : 0;

    return (
        <div className="bot-quota-cell">
            <div className="bot-quota-header">
                <strong className="bot-quota-value">
                    {activity.dailyLimitUsed} / {activity.dailyLimit}
                </strong>
                <span className="bot-quota-percentage">{percentage}%</span>
            </div>
            <progress
                className="bot-quota-progress"
                max={safeLimit}
                value={safeUsed}
                aria-label={`Wykorzystano ${activity.dailyLimitUsed} z ${activity.dailyLimit} dziennego limitu realnych akcji ofertowych`}
            />
            <span className="bot-quota-remaining">
                Pozostało: <strong>{activity.dailyLimitRemaining}</strong>
            </span>
            <span className="bot-quota-remaining">
                Audyt: <strong>{activity.confirmedActionsToday}</strong> potw.
                {activity.ambiguousActionsToday > 0
                    ? ` · ${activity.ambiguousActionsToday} niejedn.`
                    : ""}
            </span>
            {activity.usedSlotsWithoutAuditYet > 0 && (
                <span className="bot-quota-remaining">
                    W toku / bez audytu: <strong>{activity.usedSlotsWithoutAuditYet}</strong>
                </span>
            )}
        </div>
    );
}

function BotNegotiationStepsCell({ activity }: { activity: BotDailyActivity }) {
    const total = activity.nextStepsInNegotiationsStartedToday
        + activity.nextStepsInOlderNegotiations;

    return (
        <div className="bot-name-cell">
            <strong>{total}</strong>
            <span>Dziś rozpoczęte: {activity.nextStepsInNegotiationsStartedToday}</span>
            <span>Starsze: {activity.nextStepsInOlderNegotiations}</span>
        </div>
    );
}

function BotStatus({
    status,
    runtime,
    nowMs,
}: {
    status: string;
    runtime: BotRuntimeState | undefined;
    nowMs: number;
}) {
    const normalizedStatus = status.toUpperCase();
    const isRunning = normalizedStatus === "RUNNING";
    const blockedSince = isRunning ? runtime?.sessionBlockedSince : null;

    if (blockedSince) {
        const nextRetry = runtime?.nextRunAt
            ? Date.parse(runtime.nextRunAt)
            : Number.NaN;
        const retryText = Number.isFinite(nextRetry) && nextRetry > nowMs
            ? `ponownie za ${formatRemainingDuration(nextRetry - nowMs)}`
            : "trwa ponowna próba";

        return (
            <div className="bot-name-cell">
                <span className="bot-status bot-status-stopped">
                    <span className="bot-status-dot" />
                    Sesja zablokowana
                </span>
                <span>
                    od {formatElapsedDuration(blockedSince, nowMs)}
                </span>
                <span>
                    Próba {Math.max(runtime?.sessionBlockCount ?? 1, 1)} · {retryText}
                </span>
            </div>
        );
    }

    const statusClassName = isRunning
        ? "bot-status bot-status-running"
        : "bot-status bot-status-stopped";

    return (
        <span className={statusClassName}>
            <span className="bot-status-dot" />
            {formatBotStatus(status)}
        </span>
    );
}

function formatElapsedDuration(blockedSince: string, nowMs: number): string {
    const startedAt = Date.parse(blockedSince);
    if (!Number.isFinite(startedAt)) {
        return "nieznanego czasu";
    }

    const totalMinutes = Math.max(0, Math.floor((nowMs - startedAt) / 60_000));
    if (totalMinutes < 1) {
        return "mniej niż 1 min";
    }
    if (totalMinutes < 60) {
        return `${totalMinutes} min`;
    }

    const totalHours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    if (totalHours < 24) {
        return `${totalHours} godz.${minutes > 0 ? ` ${minutes} min` : ""}`;
    }

    const days = Math.floor(totalHours / 24);
    const hours = totalHours % 24;
    return `${days} d${hours > 0 ? ` ${hours} godz.` : ""}${minutes > 0 ? ` ${minutes} min` : ""}`;
}

function formatRemainingDuration(durationMs: number): string {
    const totalMinutes = Math.max(1, Math.ceil(durationMs / 60_000));
    if (totalMinutes < 60) {
        return `${totalMinutes} min`;
    }

    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return `${hours} godz.${minutes > 0 ? ` ${minutes} min` : ""}`;
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

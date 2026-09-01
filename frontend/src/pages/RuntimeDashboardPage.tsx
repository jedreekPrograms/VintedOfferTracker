import {
    useCallback,
    useEffect,
    useMemo,
    useState,
} from "react";

import {
    getRuntimeDashboard,
    type RuntimeDashboardBot,
    type RuntimeDashboardResponse,
    type RuntimeStatus,
} from "../api/dashboardApi";

import AppSelect, {
    type AppSelectOption,
} from "../components/AppSelect";

const runtimeStatuses: Array<RuntimeStatus | "ALL"> = [
    "ALL",
    "WORKING",
    "QUEUED",
    "COOLDOWN",
    "ERROR",
    "IDLE",
];

const runtimeStatusOptions: AppSelectOption[] = runtimeStatuses.map(status => ({
    value: status,
    label: status === "ALL"
        ? "Wszystkie statusy"
        : status,
}));

function RuntimeDashboardPage() {
    const [data, setData] = useState<RuntimeDashboardResponse | null>(null);
    const [statusFilter, setStatusFilter] = useState<RuntimeStatus | "ALL">("ALL");
    const [search, setSearch] = useState("");
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);
    const [nowMs, setNowMs] = useState(() => Date.now());

    const loadRuntime = useCallback(async (showLoading: boolean) => {
        if (showLoading) {
            setIsLoading(true);
        }

        try {
            const response = await getRuntimeDashboard();
            setData(response);
            setErrorMessage(null);
        } catch (error) {
            setErrorMessage(
                error instanceof Error
                    ? error.message
                    : "Nie udało się pobrać stanu runtime.",
            );
        } finally {
            if (showLoading) {
                setIsLoading(false);
            }
        }
    }, []);

    useEffect(() => {
        void loadRuntime(true);

        const intervalId = window.setInterval(
            () => {
                void loadRuntime(false);
            },
            5_000,
        );

        return () => {
            window.clearInterval(intervalId);
        };
    }, [loadRuntime]);

    useEffect(() => {
        const intervalId = window.setInterval(
            () => setNowMs(Date.now()),
            1_000,
        );

        return () => {
            window.clearInterval(intervalId);
        };
    }, []);

    const filteredBots = useMemo(() => {
        if (data === null) {
            return [];
        }

        const normalizedSearch = search.trim().toLowerCase();

        return data.bots.filter(bot => {
            if (
                statusFilter !== "ALL"
                && bot.runtimeStatus !== statusFilter
            ) {
                return false;
            }

            if (normalizedSearch.length === 0) {
                return true;
            }

            return bot.name.toLowerCase().includes(normalizedSearch)
                || bot.botId.toString().includes(normalizedSearch);
        });
    }, [data, search, statusFilter]);

    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">
                        Scheduler
                    </p>

                    <h1 className="page-title">
                        Runtime
                    </h1>

                    <p className="page-description">
                        Bieżący stan kolejki, workerów i ostatnich wykonań botów.
                        Dane odświeżają się automatycznie co 5 sekund.
                    </p>
                </div>

                <button
                    className="secondary-button"
                    type="button"
                    disabled={isLoading}
                    onClick={() => {
                        void loadRuntime(true);
                    }}
                >
                    {isLoading ? "Odświeżanie..." : "Odśwież"}
                </button>
            </header>

            {errorMessage !== null && (
                <div
                    className="form-message form-message-error"
                    role="alert"
                >
                    {errorMessage}
                </div>
            )}

            {data !== null && (
                <>
                    <div className="stats-grid">
                        <RuntimeStat
                            label="RUNNING"
                            value={data.runningBots}
                            description={`z ${data.totalBots} wszystkich botów`}
                        />
                        <RuntimeStat
                            label="WORKING"
                            value={data.workingCount}
                            description="aktualnie zajęte botami"
                        />
                        <RuntimeStat
                            label="QUEUED"
                            value={data.queuedCount}
                            description="czekają na następny job"
                        />
                        <RuntimeStat
                            label="COOLDOWN"
                            value={data.cooldownCount}
                            description="czasowo wstrzymane"
                        />
                        <RuntimeStat
                            label="ERROR"
                            value={data.errorCount}
                            description="ostatni job zakończył się błędem"
                        />
                        <RuntimeStat
                            label="Średni job"
                            value={formatDuration(data.averageLastRunDurationMs)}
                            description="średni czas ostatnich wykonań"
                        />
                    </div>

                    <article className="content-card runtime-card">
                        <div className="runtime-toolbar">
                            <div className="runtime-toolbar-copy">
                                <h2 className="content-card-title">
                                    Boty runtime
                                </h2>
                                <p className="content-card-text">
                                    Pokazano {filteredBots.length} z {data.bots.length} botów.
                                </p>
                            </div>

                            <div className="runtime-filter-controls">
                                <input
                                    className="form-input"
                                    type="search"
                                    value={search}
                                    placeholder="Nazwa lub ID bota"
                                    aria-label="Szukaj bota po nazwie lub ID"
                                    onChange={event => setSearch(event.target.value)}
                                />

                                <AppSelect
                                    value={statusFilter}
                                    ariaLabel="Filtr statusu runtime"
                                    options={runtimeStatusOptions}
                                    onChange={value => {
                                        setStatusFilter(
                                            value as RuntimeStatus | "ALL",
                                        );
                                    }}
                                />
                            </div>
                        </div>

                        <div className="runtime-table-wrapper">
                            <table className="runtime-table">
                                <thead>
                                    <tr>
                                        <TableHeader>Bot</TableHeader>
                                        <TableHeader>Bot status</TableHeader>
                                        <TableHeader>Runtime</TableHeader>
                                        <TableHeader>Slot</TableHeader>
                                        <TableHeader>Ostatni job</TableHeader>
                                        <TableHeader>Następny job</TableHeader>
                                        <TableHeader>Błędy z rzędu</TableHeader>
                                        <TableHeader>Ostatni błąd</TableHeader>
                                    </tr>
                                </thead>

                                <tbody>
                                    {filteredBots.map(bot => (
                                        <RuntimeRow
                                            key={bot.botId}
                                            bot={bot}
                                            nowMs={nowMs}
                                        />
                                    ))}
                                </tbody>
                            </table>
                        </div>

                        {filteredBots.length === 0 && (
                            <div className="dictionary-list-state">
                                Brak botów pasujących do wybranego filtra.
                            </div>
                        )}
                    </article>
                </>
            )}

            {isLoading && data === null && (
                <article className="content-card">
                    <div className="dictionary-list-state">
                        Pobieranie danych runtime...
                    </div>
                </article>
            )}
        </section>
    );
}

function RuntimeStat({
    label,
    value,
    description,
}: {
    label: string;
    value: number | string;
    description: string;
}) {
    return (
        <article className="stat-card">
            <div className="stat-label">
                {label}
            </div>
            <div className="stat-value">
                {value}
            </div>
            <div className="stat-description">
                {description}
            </div>
        </article>
    );
}

function RuntimeRow({
    bot,
    nowMs,
}: {
    bot: RuntimeDashboardBot;
    nowMs: number;
}) {
    const sessionBlocked = bot.sessionBlockedSince !== null;

    return (
        <tr>
            <TableCell label="Bot">
                <strong>{bot.name}</strong>
                <div className="runtime-cell-secondary">
                    #{bot.botId}
                </div>
            </TableCell>
            <TableCell label="Bot status">
                {bot.botStatus}
            </TableCell>
            <TableCell label="Runtime">
                <RuntimeStateCell
                    bot={bot}
                    nowMs={nowMs}
                />
            </TableCell>
            <TableCell label="Slot">
                {bot.workerSlot === null ? "—" : `#${bot.workerSlot}`}
            </TableCell>
            <TableCell label="Ostatni job">
                <div>{formatDateTime(bot.lastRunFinishedAt)}</div>
                <div className="runtime-cell-secondary">
                    {formatDuration(bot.lastRunDurationMs)}
                </div>
            </TableCell>
            <TableCell label="Następny job">
                {sessionBlocked ? (
                    <>
                        <div>{formatDateTime(bot.nextRunAt)}</div>
                        <div className="runtime-cell-secondary">
                            {formatRetryCountdown(bot.nextRunAt, nowMs)}
                        </div>
                    </>
                ) : (
                    formatDateTime(bot.nextRunAt)
                )}
            </TableCell>
            <TableCell label="Błędy z rzędu">
                {bot.consecutiveFailures}
            </TableCell>
            <TableCell label="Ostatni błąd">
                <div
                    className="runtime-error-text"
                    title={bot.lastError ?? undefined}
                >
                    {bot.lastError ?? "—"}
                </div>
            </TableCell>
        </tr>
    );
}

function RuntimeStateCell({
    bot,
    nowMs,
}: {
    bot: RuntimeDashboardBot;
    nowMs: number;
}) {
    if (bot.sessionBlockedSince !== null) {
        return (
            <div>
                <RuntimeBadge
                    status={bot.runtimeStatus}
                    label="SESSION BLOCKED"
                />
                <div className="runtime-cell-secondary">
                    Sesja zablokowana od {formatElapsedDuration(bot.sessionBlockedSince, nowMs)}
                </div>
                <div className="runtime-cell-secondary">
                    Próba {Math.max(bot.sessionBlockCount, 1)} · {formatRetryCountdown(bot.nextRunAt, nowMs)}
                </div>
            </div>
        );
    }

    return <RuntimeBadge status={bot.runtimeStatus} />;
}

function RuntimeBadge({
    status,
    label,
}: {
    status: RuntimeStatus;
    label?: string;
}) {
    return (
        <span className={`runtime-badge runtime-badge-${status.toLowerCase()}`}>
            {label ?? status}
        </span>
    );
}

function TableHeader({ children }: { children: React.ReactNode }) {
    return <th>{children}</th>;
}

function TableCell({
    children,
    label,
}: {
    children: React.ReactNode;
    label: string;
}) {
    return (
        <td data-label={label}>
            {children}
        </td>
    );
}

function formatDateTime(value: string | null): string {
    if (value === null) {
        return "—";
    }

    const date = new Date(value);

    if (Number.isNaN(date.getTime())) {
        return "—";
    }

    return new Intl.DateTimeFormat(
        "pl-PL",
        {
            dateStyle: "short",
            timeStyle: "medium",
        },
    ).format(date);
}

function formatDuration(value: number | null): string {
    if (value === null || !Number.isFinite(value) || value < 0) {
        return "—";
    }

    if (value < 1_000) {
        return `${Math.round(value)} ms`;
    }

    return `${(value / 1_000).toFixed(1)} s`;
}

function formatElapsedDuration(value: string, nowMs: number): string {
    const startedAt = Date.parse(value);
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

function formatRetryCountdown(value: string | null, nowMs: number): string {
    if (value === null) {
        return "trwa ponowna próba";
    }

    const nextRunAt = Date.parse(value);
    if (!Number.isFinite(nextRunAt) || nextRunAt <= nowMs) {
        return "trwa ponowna próba";
    }

    const totalSeconds = Math.max(1, Math.ceil((nextRunAt - nowMs) / 1_000));
    if (totalSeconds < 60) {
        return `ponownie za ${totalSeconds} s`;
    }

    const totalMinutes = Math.ceil(totalSeconds / 60);
    if (totalMinutes < 60) {
        return `ponownie za ${totalMinutes} min`;
    }

    const hours = Math.floor(totalMinutes / 60);
    const minutes = totalMinutes % 60;
    return `ponownie za ${hours} godz.${minutes > 0 ? ` ${minutes} min` : ""}`;
}

export default RuntimeDashboardPage;

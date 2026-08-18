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
                                        <TableHeader>Błędy</TableHeader>
                                        <TableHeader>Ostatni błąd</TableHeader>
                                    </tr>
                                </thead>

                                <tbody>
                                    {filteredBots.map(bot => (
                                        <RuntimeRow
                                            key={bot.botId}
                                            bot={bot}
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
}: {
    bot: RuntimeDashboardBot;
}) {
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
                <RuntimeBadge status={bot.runtimeStatus} />
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
                {formatDateTime(bot.nextRunAt)}
            </TableCell>
            <TableCell label="Błędy">
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

function RuntimeBadge({ status }: { status: RuntimeStatus }) {
    return (
        <span className={`runtime-badge runtime-badge-${status.toLowerCase()}`}>
            {status}
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

export default RuntimeDashboardPage;

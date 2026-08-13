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

const runtimeStatuses: Array<RuntimeStatus | "ALL"> = [
    "ALL",
    "WORKING",
    "QUEUED",
    "COOLDOWN",
    "ERROR",
    "IDLE",
];

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

                    <article className="content-card" style={{ marginTop: "24px" }}>
                        <div
                            style={{
                                display: "flex",
                                gap: "12px",
                                flexWrap: "wrap",
                                alignItems: "center",
                                justifyContent: "space-between",
                                marginBottom: "18px",
                            }}
                        >
                            <div>
                                <h2 className="content-card-title">
                                    Boty runtime
                                </h2>
                                <p className="content-card-text">
                                    Pokazano {filteredBots.length} z {data.bots.length} botów.
                                </p>
                            </div>

                            <div
                                style={{
                                    display: "flex",
                                    gap: "10px",
                                    flexWrap: "wrap",
                                }}
                            >
                                <input
                                    className="form-input"
                                    type="search"
                                    value={search}
                                    placeholder="Nazwa lub ID bota"
                                    onChange={event => setSearch(event.target.value)}
                                    style={{ minWidth: "220px" }}
                                />

                                <select
                                    className="form-input"
                                    value={statusFilter}
                                    onChange={event => {
                                        setStatusFilter(
                                            event.target.value as RuntimeStatus | "ALL",
                                        );
                                    }}
                                >
                                    {runtimeStatuses.map(status => (
                                        <option
                                            key={status}
                                            value={status}
                                        >
                                            {status === "ALL" ? "Wszystkie statusy" : status}
                                        </option>
                                    ))}
                                </select>
                            </div>
                        </div>

                        <div style={{ overflowX: "auto" }}>
                            <table
                                style={{
                                    width: "100%",
                                    borderCollapse: "collapse",
                                    minWidth: "1080px",
                                }}
                            >
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
            <TableCell>
                <strong>{bot.name}</strong>
                <div style={{ opacity: 0.65, marginTop: "3px" }}>
                    #{bot.botId}
                </div>
            </TableCell>
            <TableCell>{bot.botStatus}</TableCell>
            <TableCell>
                <RuntimeBadge status={bot.runtimeStatus} />
            </TableCell>
            <TableCell>
                {bot.workerSlot === null ? "—" : `#${bot.workerSlot}`}
            </TableCell>
            <TableCell>
                <div>{formatDateTime(bot.lastRunFinishedAt)}</div>
                <div style={{ opacity: 0.65, marginTop: "3px" }}>
                    {formatDuration(bot.lastRunDurationMs)}
                </div>
            </TableCell>
            <TableCell>{formatDateTime(bot.nextRunAt)}</TableCell>
            <TableCell>{bot.consecutiveFailures}</TableCell>
            <TableCell>
                <div
                    title={bot.lastError ?? undefined}
                    style={{
                        maxWidth: "300px",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                    }}
                >
                    {bot.lastError ?? "—"}
                </div>
            </TableCell>
        </tr>
    );
}

function RuntimeBadge({ status }: { status: RuntimeStatus }) {
    const background =
        status === "ERROR"
            ? "rgba(220, 38, 38, 0.14)"
            : status === "WORKING"
                ? "rgba(22, 163, 74, 0.14)"
                : status === "COOLDOWN"
                    ? "rgba(217, 119, 6, 0.14)"
                    : "rgba(100, 116, 139, 0.14)";

    return (
        <span
            style={{
                display: "inline-flex",
                padding: "5px 9px",
                borderRadius: "999px",
                background,
                fontWeight: 700,
                fontSize: "12px",
            }}
        >
            {status}
        </span>
    );
}

function TableHeader({ children }: { children: React.ReactNode }) {
    return (
        <th
            style={{
                textAlign: "left",
                padding: "12px",
                borderBottom: "1px solid rgba(148, 163, 184, 0.25)",
                fontSize: "12px",
                textTransform: "uppercase",
                letterSpacing: "0.04em",
            }}
        >
            {children}
        </th>
    );
}

function TableCell({ children }: { children: React.ReactNode }) {
    return (
        <td
            style={{
                padding: "13px 12px",
                borderBottom: "1px solid rgba(148, 163, 184, 0.14)",
                verticalAlign: "top",
            }}
        >
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

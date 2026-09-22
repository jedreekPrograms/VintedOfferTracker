import {
    useCallback,
    useEffect,
    useState,
} from "react";

import {
    getDashboardStats,
    type DashboardPeriod,
    type DashboardStatsResponse,
} from "../api/dashboardApi";
import type {
    HistoryOutcome,
    OfferAssessment,
} from "../api/historyApi";

interface DashboardStat {
    label: string;
    value: string;
    description: string;
    variant?: "default" | "success" | "warning";
}

interface PeriodOption {
    value: DashboardPeriod;
    label: string;
}

const periodOptions: PeriodOption[] = [
    { value: "TODAY", label: "Dzisiaj" },
    { value: "LAST_7_DAYS", label: "7 dni" },
    { value: "LAST_30_DAYS", label: "30 dni" },
    { value: "THIS_MONTH", label: "Ten miesiąc" },
    { value: "THIS_YEAR", label: "Ten rok" },
    { value: "ALL", label: "Całość" },
];

const outcomeOptions: Array<{
    value: HistoryOutcome;
    label: string;
}> = [
    { value: "PURCHASED", label: "Kupione" },
    { value: "REJECTED", label: "Odrzucone" },
    { value: "MISSED_OPPORTUNITY", label: "Utracone okazje" },
    { value: "UNCLASSIFIED", label: "Do oznaczenia" },
];

const assessmentOptions: Array<{
    value: OfferAssessment;
    label: string;
}> = [
    { value: "LEGIT", label: "Legit" },
    { value: "SCAM", label: "Oszustwo" },
    { value: "UNASSESSED", label: "Nieocenione" },
];

function DashboardPage() {
    const [stats, setStats] = useState<DashboardStatsResponse | null>(null);
    const [period, setPeriod] = useState<DashboardPeriod>("ALL");
    const [outcomes, setOutcomes] = useState<HistoryOutcome[]>([]);
    const [assessments, setAssessments] = useState<OfferAssessment[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const loadStats = useCallback(async () => {
        setIsLoading(true);
        setErrorMessage(null);

        try {
            setStats(
                await getDashboardStats(
                    period,
                    outcomes,
                    assessments,
                ),
            );
        } catch (error) {
            setErrorMessage(
                error instanceof Error
                    ? error.message
                    : "Nie udało się pobrać statystyk.",
            );
        } finally {
            setIsLoading(false);
        }
    }, [period, outcomes, assessments]);

    useEffect(() => {
        void loadStats();
    }, [loadStats]);

    const activityStats: DashboardStat[] =
        stats === null
            ? []
            : [
                {
                    label: "Aktywne boty",
                    value: stats.activeBotsCount.toString(),
                    description: "Boty ze statusem RUNNING",
                },
                {
                    label: "Trwające negocjacje",
                    value: stats.negotiatingCount.toString(),
                    description: "Oferty ze statusem NEGOTIATING",
                },
                {
                    label: "Oferty do kupienia",
                    value: stats.actionRequiredCount.toString(),
                    description: "Czekają na Twoją decyzję",
                    variant: stats.actionRequiredCount > 0
                        ? "warning"
                        : "default",
                },
            ];

    const periodStats: DashboardStat[] =
        stats === null
            ? []
            : [
                {
                    label: "Wybrane oferty",
                    value: stats.selectedHistoryCount.toString(),
                    description: "Po zastosowaniu filtrów",
                },
                {
                    label: "Kupione",
                    value: stats.purchasedCount.toString(),
                    description: getPeriodDescription(period),
                    variant: "success",
                },
                {
                    label: "Odrzucone",
                    value: stats.skippedByUserCount.toString(),
                    description: "Oferty sklasyfikowane jako odrzucone",
                },
                {
                    label: "Utracone okazje",
                    value: stats.missedOpportunityCount.toString(),
                    description: "Legit okazje, których nie udało się kupić",
                    variant: "warning",
                },
                {
                    label: "Legit",
                    value: stats.legitCount.toString(),
                    description: "Oferty oznaczone jako wiarygodne",
                    variant: "success",
                },
                {
                    label: "Oszustwa",
                    value: stats.scamCount.toString(),
                    description: "Oferty oznaczone jako oszustwo/podejrzane",
                },
                {
                    label: "Łącznie wydano",
                    value: formatPrice(stats.totalSpent),
                    description: "Suma cen ofert sklasyfikowanych jako kupione",
                },
                {
                    label: "Wynegocjowano",
                    value: formatPrice(stats.totalNegotiatedSavings),
                    description: "Różnica względem cen początkowych",
                    variant: "success",
                },
                {
                    label: "Średnia cena zakupu",
                    value: formatPrice(stats.averagePurchasePrice),
                    description: "Tylko oferty sklasyfikowane jako kupione",
                },
                {
                    label: "Średni rabat",
                    value: formatPercentage(stats.averageDiscountPercentage),
                    description: "Średnia obniżka ceny kupionych ofert",
                    variant: "success",
                },
                {
                    label: "Średnia cena wybranych",
                    value: formatNullablePrice(stats.averageSelectedPrice),
                    description: "Wszystkie oferty przechodzące aktualne filtry",
                },
                {
                    label: "Mediana ceny wybranych",
                    value: formatNullablePrice(stats.medianSelectedPrice),
                    description: "Odporna na pojedyncze bardzo drogie/tanie oferty",
                },
            ];

    const purchaseRate =
        stats === null
            ? 0
            : calculatePurchaseRate(
                stats.purchasedCount,
                stats.skippedByUserCount,
            );

    return (
        <section className="page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">Panel główny</p>
                    <h1 className="page-title">Dashboard</h1>
                    <p className="page-description">
                        Podsumowanie działania botów, negocjacji i wyników ofert.
                    </p>
                </div>

                <button
                    className="secondary-button"
                    type="button"
                    disabled={isLoading}
                    onClick={() => void loadStats()}
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

            <div className="dashboard-section-header">
                <div>
                    <h2>Aktualny stan</h2>
                    <p>Dane operacyjne niezależne od filtrów historii.</p>
                </div>
            </div>

            {stats !== null && (
                <div className="stats-grid">
                    {activityStats.map(stat => (
                        <StatCard
                            key={stat.label}
                            stat={stat}
                        />
                    ))}
                </div>
            )}

            <div className="dashboard-period-section">
                <div className="dashboard-section-header">
                    <div>
                        <h2>Wyniki</h2>
                        <p>
                            Wybierz okres oraz typy ofert, które mają wejść do podsumowania.
                        </p>
                    </div>
                </div>

                <div className="dashboard-period-switch">
                    {periodOptions.map(option => (
                        <button
                            key={option.value}
                            className={
                                period === option.value
                                    ? "dashboard-period-button dashboard-period-button-active"
                                    : "dashboard-period-button"
                            }
                            type="button"
                            disabled={isLoading}
                            onClick={() => setPeriod(option.value)}
                        >
                            {option.label}
                        </button>
                    ))}
                </div>

                <DashboardFilterGroup
                    title="Wynik oferty"
                    allLabel="Wszystkie"
                    options={outcomeOptions}
                    selected={outcomes}
                    disabled={isLoading}
                    onChange={setOutcomes}
                />

                <DashboardFilterGroup
                    title="Ocena oferty"
                    allLabel="Wszystkie oceny"
                    options={assessmentOptions}
                    selected={assessments}
                    disabled={isLoading}
                    onChange={setAssessments}
                />
            </div>

            {isLoading && stats === null ? (
                <article className="content-card">
                    <div className="dictionary-list-state">
                        Pobieranie statystyk...
                    </div>
                </article>
            ) : stats !== null ? (
                <>
                    <div className="stats-grid">
                        {periodStats.map(stat => (
                            <StatCard
                                key={stat.label}
                                stat={stat}
                            />
                        ))}
                    </div>

                    <div className="dashboard-summary-grid">
                        <article className="content-card">
                            <h2 className="content-card-title">
                                Decyzje zakupowe
                            </h2>
                            <p className="content-card-text">
                                Wyniki ofert po aktualnie wybranych filtrach.
                            </p>

                            <div className="dashboard-decision-stats">
                                <div>
                                    <span>Kupione</span>
                                    <strong>{stats.purchasedCount}</strong>
                                </div>
                                <div>
                                    <span>Odrzucone</span>
                                    <strong>{stats.skippedByUserCount}</strong>
                                </div>
                                <div>
                                    <span>Utracone</span>
                                    <strong>{stats.missedOpportunityCount}</strong>
                                </div>
                            </div>
                        </article>

                        <article className="content-card">
                            <h2 className="content-card-title">
                                Skuteczność zakupu
                            </h2>
                            <p className="content-card-text">
                                Jak duża część wpisów Kupione/Odrzucone zakończyła się zakupem.
                            </p>

                            <div className="dashboard-effectiveness">
                                <strong>{formatPercentage(purchaseRate)}</strong>
                                <span>ofert zakończonych zakupem</span>
                            </div>
                        </article>
                    </div>

                    <article className="content-card">
                        <h2 className="content-card-title">
                            Aktywne filtry
                        </h2>
                        <p className="content-card-text">
                            Okres: <strong>{getPeriodLabel(period)}</strong>.
                            {" "}Wynik: <strong>{formatSelection(
                                outcomes,
                                outcomeOptions,
                                "wszystkie",
                            )}</strong>.
                            {" "}Ocena: <strong>{formatSelection(
                                assessments,
                                assessmentOptions,
                                "wszystkie",
                            )}</strong>.
                        </p>
                        <p className="content-card-text">
                            Kwoty zakupowe nadal są liczone wyłącznie z ofert
                            sklasyfikowanych jako Kupione. Pozostałe filtry wpływają
                            na liczbę ofert, średnią i medianę wybranego zbioru.
                        </p>
                    </article>
                </>
            ) : null}
        </section>
    );
}

function DashboardFilterGroup<T extends string>({
    title,
    allLabel,
    options,
    selected,
    disabled,
    onChange,
}: {
    title: string;
    allLabel: string;
    options: Array<{ value: T; label: string }>;
    selected: T[];
    disabled: boolean;
    onChange: (value: T[]) => void;
}) {
    function toggle(value: T) {
        onChange(
            selected.includes(value)
                ? selected.filter(item => item !== value)
                : [...selected, value],
        );
    }

    return (
        <div className="dashboard-filter-group">
            <span>{title}</span>
            <div className="dashboard-period-switch">
                <button
                    className={
                        selected.length === 0
                            ? "dashboard-period-button dashboard-period-button-active"
                            : "dashboard-period-button"
                    }
                    type="button"
                    disabled={disabled}
                    onClick={() => onChange([])}
                >
                    {allLabel}
                </button>

                {options.map(option => (
                    <button
                        key={option.value}
                        className={
                            selected.includes(option.value)
                                ? "dashboard-period-button dashboard-period-button-active"
                                : "dashboard-period-button"
                        }
                        type="button"
                        disabled={disabled}
                        onClick={() => toggle(option.value)}
                    >
                        {option.label}
                    </button>
                ))}
            </div>
        </div>
    );
}

function StatCard({
    stat,
}: {
    stat: DashboardStat;
}) {
    return (
        <article
            className={
                [
                    "stat-card",
                    stat.variant !== undefined
                        ? `stat-card-${stat.variant}`
                        : "",
                ]
                    .filter(Boolean)
                    .join(" ")
            }
        >
            <div className="stat-label">{stat.label}</div>
            <div className="stat-value">{stat.value}</div>
            <div className="stat-description">{stat.description}</div>
        </article>
    );
}

function calculatePurchaseRate(
    purchasedCount: number,
    skippedCount: number,
): number {
    const total = purchasedCount + skippedCount;

    if (total === 0) {
        return 0;
    }

    return (purchasedCount / total) * 100;
}

function getPeriodDescription(
    period: DashboardPeriod,
): string {
    switch (period) {
        case "TODAY":
            return "Kupione dzisiaj";
        case "LAST_7_DAYS":
            return "Kupione w ostatnich 7 dniach";
        case "LAST_30_DAYS":
            return "Kupione w ostatnich 30 dniach";
        case "THIS_MONTH":
            return "Kupione w tym miesiącu";
        case "THIS_YEAR":
            return "Kupione w tym roku";
        case "ALL":
            return "Kupione od początku";
    }
}

function getPeriodLabel(
    period: DashboardPeriod,
): string {
    switch (period) {
        case "TODAY":
            return "dzisiaj";
        case "LAST_7_DAYS":
            return "ostatnie 7 dni";
        case "LAST_30_DAYS":
            return "ostatnie 30 dni";
        case "THIS_MONTH":
            return "ten miesiąc";
        case "THIS_YEAR":
            return "ten rok";
        case "ALL":
            return "cały okres";
    }
}

function formatSelection<T extends string>(
    selected: T[],
    options: Array<{ value: T; label: string }>,
    fallback: string,
): string {
    if (selected.length === 0) {
        return fallback;
    }

    return selected
        .map(value => options.find(option => option.value === value)?.label ?? value)
        .join(", ");
}

function formatPrice(
    value: number,
): string {
    return new Intl.NumberFormat(
        "pl-PL",
        {
            style: "currency",
            currency: "PLN",
            maximumFractionDigits: 2,
        },
    ).format(value);
}

function formatNullablePrice(
    value: number | null,
): string {
    return value === null
        ? "—"
        : formatPrice(value);
}

function formatPercentage(
    value: number,
): string {
    return new Intl.NumberFormat(
        "pl-PL",
        {
            minimumFractionDigits: 1,
            maximumFractionDigits: 1,
        },
    ).format(value) + "%";
}

export default DashboardPage;

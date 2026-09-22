import {
    useCallback,
    useEffect,
    useMemo,
    useState,
} from "react";

import {
    getAnalyticsOverview,
    type AnalyticsHistogramBucket,
    type AnalyticsModelBreakdown,
    type AnalyticsOverview,
    type AnalyticsSource,
    type AnalyticsTimelinePoint,
} from "../api/analyticsApi";
import type {
    DashboardPeriod,
} from "../api/dashboardApi";
import type {
    HistoryOutcome,
    OfferAssessment,
} from "../api/historyApi";
import AppSelect, {
    type AppSelectOption,
} from "../components/AppSelect";
import "../styles/analytics.css";

const periodOptions: Array<{
    value: DashboardPeriod;
    label: string;
}> = [
    { value: "TODAY", label: "Dzisiaj" },
    { value: "LAST_7_DAYS", label: "7 dni" },
    { value: "LAST_30_DAYS", label: "30 dni" },
    { value: "THIS_MONTH", label: "Ten miesiąc" },
    { value: "THIS_YEAR", label: "Ten rok" },
    { value: "ALL", label: "Całość" },
];

const sourceOptions: AppSelectOption[] = [
    { value: "ALL", label: "Historia + Observer" },
    { value: "HISTORY", label: "Tylko Historia" },
    { value: "OBSERVER", label: "Tylko Observer" },
];

const outcomeOptions: Array<{
    value: HistoryOutcome;
    label: string;
}> = [
    { value: "PURCHASED", label: "Kupione" },
    { value: "REJECTED", label: "Odrzucone" },
    { value: "MISSED_OPPORTUNITY", label: "Utracone" },
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

function StatisticsPage() {
    const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
    const [period, setPeriod] = useState<DashboardPeriod>("THIS_MONTH");
    const [modelId, setModelId] = useState<number | null>(null);
    const [source, setSource] = useState<AnalyticsSource>("ALL");
    const [outcomes, setOutcomes] = useState<HistoryOutcome[]>([]);
    const [assessments, setAssessments] = useState<OfferAssessment[]>([]);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const load = useCallback(async () => {
        setIsLoading(true);
        setErrorMessage(null);

        try {
            setOverview(
                await getAnalyticsOverview(
                    period,
                    modelId,
                    outcomes,
                    assessments,
                    source,
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
    }, [period, modelId, outcomes, assessments, source]);

    useEffect(() => {
        void load();
    }, [load]);

    const modelOptions = useMemo<AppSelectOption[]>(
        () => [
            { value: "ALL", label: "Wszystkie modele" },
            ...(overview?.models ?? []).map(model => ({
                value: String(model.modelId),
                label: `${model.brand} — ${model.model}`,
            })),
        ],
        [overview],
    );

    const summary = overview?.summary ?? null;

    return (
        <section className="page analytics-page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">Analiza danych</p>
                    <h1 className="page-title">Statystyki</h1>
                    <p className="page-description">
                        Ceny rynku z Observera oraz wyniki własnych negocjacji
                        w jednym widoku, z filtrowaniem po modelu i jakości oferty.
                    </p>
                </div>

                <button
                    className="secondary-button"
                    type="button"
                    disabled={isLoading}
                    onClick={() => void load()}
                >
                    {isLoading ? "Odświeżanie..." : "Odśwież"}
                </button>
            </header>

            {errorMessage !== null && (
                <div className="form-message form-message-error" role="alert">
                    {errorMessage}
                </div>
            )}

            <article className="content-card analytics-filter-card">
                <div className="analytics-filter-row">
                    <div className="analytics-filter-field">
                        <label>Model</label>
                        <AppSelect
                            value={modelId === null ? "ALL" : String(modelId)}
                            options={modelOptions}
                            disabled={isLoading}
                            ariaLabel="Model do analizy"
                            onChange={value => setModelId(
                                value === "ALL"
                                    ? null
                                    : Number(value),
                            )}
                        />
                    </div>

                    <div className="analytics-filter-field">
                        <label>Źródło danych</label>
                        <AppSelect
                            value={source}
                            options={sourceOptions}
                            disabled={isLoading}
                            ariaLabel="Źródło danych"
                            onChange={value => setSource(value as AnalyticsSource)}
                        />
                    </div>
                </div>

                <FilterPills
                    title="Okres"
                    allMode={false}
                    options={periodOptions}
                    selected={[period]}
                    disabled={isLoading}
                    onSingleChange={value => setPeriod(value as DashboardPeriod)}
                />

                <FilterPills
                    title="Wynik historii"
                    allLabel="Wszystkie wyniki"
                    options={outcomeOptions}
                    selected={outcomes}
                    disabled={isLoading}
                    onChange={setOutcomes}
                />

                <FilterPills
                    title="Ocena historii"
                    allLabel="Wszystkie oceny"
                    options={assessmentOptions}
                    selected={assessments}
                    disabled={isLoading}
                    onChange={setAssessments}
                />
            </article>

            {isLoading && overview === null ? (
                <article className="content-card">
                    <div className="dictionary-list-state">
                        Pobieranie statystyk...
                    </div>
                </article>
            ) : summary !== null && overview !== null ? (
                <>
                    <div className="analytics-kpi-grid">
                        <AnalyticsKpi
                            label="Oferty rynku"
                            value={String(summary.marketListingCount)}
                            detail={`${summary.marketPriceSampleCount} z ceną`}
                        />
                        <AnalyticsKpi
                            label="Mediana rynku"
                            value={formatNullablePrice(summary.medianMarketPrice)}
                            detail="Observer — cena pierwszej zapisanej obserwacji"
                        />
                        <AnalyticsKpi
                            label="Średnia rynku"
                            value={formatNullablePrice(summary.averageMarketPrice)}
                            detail="Tylko rekordy z dostępną ceną"
                        />
                        <AnalyticsKpi
                            label="Kupione"
                            value={String(summary.purchasedCount)}
                            detail={`mediana ${formatNullablePrice(summary.medianPurchasePrice)}`}
                        />
                        <AnalyticsKpi
                            label="Średnia zakupu"
                            value={formatNullablePrice(summary.averagePurchasePrice)}
                            detail="Cena końcowa wpisów Kupione"
                        />
                        <AnalyticsKpi
                            label="Poniżej mediany rynku"
                            value={formatNullableSignedPrice(
                                summary.purchaseBelowMarketMedianAmount,
                            )}
                            detail={formatNullablePercent(
                                summary.purchaseBelowMarketMedianPercent,
                            )}
                            positive={
                                summary.purchaseBelowMarketMedianAmount !== null
                                && summary.purchaseBelowMarketMedianAmount > 0
                            }
                        />
                        <AnalyticsKpi
                            label="Odrzucone legit"
                            value={formatNullablePrice(
                                summary.medianLegitRejectedPrice,
                            )}
                            detail={`średnia ${formatNullablePrice(
                                summary.averageLegitRejectedPrice,
                            )}`}
                        />
                        <AnalyticsKpi
                            label="Utracone okazje"
                            value={String(summary.missedOpportunityCount)}
                            detail={`mediana ${formatNullablePrice(
                                summary.medianMissedOpportunityPrice,
                            )}`}
                        />
                        <AnalyticsKpi
                            label="Oszustwa"
                            value={String(summary.scamCount)}
                            detail={summary.historyCount > 0
                                ? `${formatNumber(
                                    (summary.scamCount / summary.historyCount) * 100,
                                    1,
                                )}% historii`
                                : "Brak sklasyfikowanych danych"}
                        />
                    </div>

                    <div className="analytics-grid analytics-grid-two">
                        <article className="content-card analytics-chart-card">
                            <div className="analytics-card-heading">
                                <div>
                                    <h2 className="content-card-title">
                                        Cena w czasie
                                    </h2>
                                    <p className="content-card-text">
                                        Mediana rynku z Observera i średnia naszych zakupów.
                                    </p>
                                </div>
                            </div>
                            <PriceTimelineChart points={overview.timeline} />
                        </article>

                        <article className="content-card analytics-chart-card">
                            <div className="analytics-card-heading">
                                <div>
                                    <h2 className="content-card-title">
                                        Rozkład cen rynku
                                    </h2>
                                    <p className="content-card-text">
                                        Histogram ofert z dostępną ceną.
                                    </p>
                                </div>
                            </div>
                            <HistogramChart buckets={overview.marketPriceHistogram} />
                        </article>
                    </div>

                    <article className="content-card analytics-descriptive-card">
                        <h2 className="content-card-title">
                            Statystyki opisowe rynku
                        </h2>
                        <div className="analytics-descriptive-grid">
                            <Metric label="Minimum" value={formatNullablePrice(summary.marketMinPrice)} />
                            <Metric label="P25" value={formatNullablePrice(summary.marketP25)} />
                            <Metric label="Mediana" value={formatNullablePrice(summary.medianMarketPrice)} />
                            <Metric label="P75" value={formatNullablePrice(summary.marketP75)} />
                            <Metric label="Maksimum" value={formatNullablePrice(summary.marketMaxPrice)} />
                            <Metric
                                label="Odchylenie standardowe"
                                value={formatNullablePrice(summary.marketStandardDeviation)}
                            />
                        </div>
                    </article>

                    <article className="content-card analytics-table-card">
                        <div className="analytics-card-heading">
                            <div>
                                <h2 className="content-card-title">
                                    Modele
                                </h2>
                                <p className="content-card-text">
                                    Rynek, własne zakupy i wynik negocjacji obok siebie.
                                </p>
                            </div>
                        </div>

                        <ModelTable rows={overview.modelBreakdowns} />
                    </article>
                </>
            ) : null}
        </section>
    );
}

function FilterPills<T extends string>({
    title,
    allLabel,
    allMode = true,
    options,
    selected,
    disabled,
    onChange,
    onSingleChange,
}: {
    title: string;
    allLabel?: string;
    allMode?: boolean;
    options: Array<{ value: T; label: string }>;
    selected: T[];
    disabled: boolean;
    onChange?: (value: T[]) => void;
    onSingleChange?: (value: T) => void;
}) {
    function toggle(value: T) {
        if (!allMode) {
            onSingleChange?.(value);
            return;
        }

        onChange?.(
            selected.includes(value)
                ? selected.filter(item => item !== value)
                : [...selected, value],
        );
    }

    return (
        <div className="analytics-pill-group">
            <span>{title}</span>
            <div>
                {allMode && (
                    <button
                        className={selected.length === 0
                            ? "analytics-pill analytics-pill-active"
                            : "analytics-pill"}
                        type="button"
                        disabled={disabled}
                        onClick={() => onChange?.([])}
                    >
                        {allLabel ?? "Wszystkie"}
                    </button>
                )}

                {options.map(option => (
                    <button
                        key={option.value}
                        className={selected.includes(option.value)
                            ? "analytics-pill analytics-pill-active"
                            : "analytics-pill"}
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

function AnalyticsKpi({
    label,
    value,
    detail,
    positive = false,
}: {
    label: string;
    value: string;
    detail: string;
    positive?: boolean;
}) {
    return (
        <article className={
            positive
                ? "analytics-kpi analytics-kpi-positive"
                : "analytics-kpi"
        }>
            <span>{label}</span>
            <strong>{value}</strong>
            <small>{detail}</small>
        </article>
    );
}

function Metric({
    label,
    value,
}: {
    label: string;
    value: string;
}) {
    return (
        <div className="analytics-metric">
            <span>{label}</span>
            <strong>{value}</strong>
        </div>
    );
}

function PriceTimelineChart({
    points,
}: {
    points: AnalyticsTimelinePoint[];
}) {
    const usable = points.filter(point =>
        point.medianMarketPrice !== null
        || point.averagePurchasePrice !== null,
    );

    if (usable.length === 0) {
        return <EmptyChart text="Brak danych cenowych dla tych filtrów." />;
    }

    const allValues = usable.flatMap(point => [
        point.medianMarketPrice,
        point.averagePurchasePrice,
    ]).filter((value): value is number => value !== null);

    const min = Math.min(...allValues);
    const max = Math.max(...allValues);
    const range = Math.max(max - min, 1);
    const width = 900;
    const height = 280;
    const paddingX = 48;
    const paddingY = 28;
    const chartWidth = width - paddingX * 2;
    const chartHeight = height - paddingY * 2;

    const x = (index: number) =>
        paddingX + (
            usable.length === 1
                ? chartWidth / 2
                : (index / (usable.length - 1)) * chartWidth
        );
    const y = (value: number) =>
        paddingY + chartHeight - ((value - min) / range) * chartHeight;

    const marketPath = buildSvgPath(
        usable,
        point => point.medianMarketPrice,
        x,
        y,
    );
    const purchasePath = buildSvgPath(
        usable,
        point => point.averagePurchasePrice,
        x,
        y,
    );

    return (
        <div className="analytics-timeline">
            <div className="analytics-legend">
                <span className="analytics-legend-market">Mediana rynku</span>
                <span className="analytics-legend-purchase">Śr. zakupów</span>
            </div>

            <svg
                viewBox={`0 0 ${width} ${height}`}
                role="img"
                aria-label="Wykres cen w czasie"
            >
                <line
                    className="analytics-axis"
                    x1={paddingX}
                    x2={paddingX}
                    y1={paddingY}
                    y2={height - paddingY}
                />
                <line
                    className="analytics-axis"
                    x1={paddingX}
                    x2={width - paddingX}
                    y1={height - paddingY}
                    y2={height - paddingY}
                />

                {marketPath.length > 0 && (
                    <path
                        className="analytics-line analytics-line-market"
                        d={marketPath}
                    />
                )}
                {purchasePath.length > 0 && (
                    <path
                        className="analytics-line analytics-line-purchase"
                        d={purchasePath}
                    />
                )}

                {usable.map((point, index) => (
                    <g key={point.date}>
                        {point.medianMarketPrice !== null && (
                            <circle
                                className="analytics-dot analytics-dot-market"
                                cx={x(index)}
                                cy={y(point.medianMarketPrice)}
                                r="4"
                            />
                        )}
                        {point.averagePurchasePrice !== null && (
                            <circle
                                className="analytics-dot analytics-dot-purchase"
                                cx={x(index)}
                                cy={y(point.averagePurchasePrice)}
                                r="4"
                            />
                        )}
                    </g>
                ))}
            </svg>

            <div className="analytics-chart-footer">
                <span>{formatDate(usable[0].date)}</span>
                <span>
                    {formatPrice(max)} – {formatPrice(min)}
                </span>
                <span>{formatDate(usable[usable.length - 1].date)}</span>
            </div>
        </div>
    );
}

function buildSvgPath(
    points: AnalyticsTimelinePoint[],
    value: (point: AnalyticsTimelinePoint) => number | null,
    x: (index: number) => number,
    y: (value: number) => number,
): string {
    let path = "";
    let drawing = false;

    points.forEach((point, index) => {
        const current = value(point);

        if (current === null) {
            drawing = false;
            return;
        }

        path += `${drawing ? " L" : " M"} ${x(index)} ${y(current)}`;
        drawing = true;
    });

    return path.trim();
}

function HistogramChart({
    buckets,
}: {
    buckets: AnalyticsHistogramBucket[];
}) {
    if (buckets.length === 0) {
        return <EmptyChart text="Observer nie ma jeszcze próbek cen dla tych filtrów." />;
    }

    const maxCount = Math.max(...buckets.map(bucket => bucket.count), 1);

    return (
        <div className="analytics-histogram">
            <div className="analytics-bars">
                {buckets.map((bucket, index) => (
                    <div
                        className="analytics-bar-column"
                        key={`${bucket.from}-${bucket.to}-${index}`}
                        title={`${formatPrice(bucket.from)}–${formatPrice(bucket.to)}: ${bucket.count}`}
                    >
                        <span>{bucket.count}</span>
                        <div
                            className="analytics-bar"
                            style={{
                                height: `${Math.max(
                                    8,
                                    (bucket.count / maxCount) * 170,
                                )}px`,
                            }}
                        />
                        <small>{formatCompactPrice(bucket.from)}</small>
                    </div>
                ))}
            </div>
        </div>
    );
}

function ModelTable({
    rows,
}: {
    rows: AnalyticsModelBreakdown[];
}) {
    if (rows.length === 0) {
        return <EmptyChart text="Brak danych modelowych dla tych filtrów." />;
    }

    return (
        <div className="analytics-table-wrap">
            <table className="analytics-table">
                <thead>
                    <tr>
                        <th>Model</th>
                        <th>Oferty rynku</th>
                        <th>Mediana rynku</th>
                        <th>Kupione</th>
                        <th>Śr. zakup</th>
                        <th>Mediana zakupu</th>
                        <th>Legit odrzucone</th>
                        <th>Utracone</th>
                        <th>Oszustwa</th>
                        <th>Zakup vs rynek</th>
                    </tr>
                </thead>
                <tbody>
                    {rows.map(row => (
                        <tr key={row.modelId}>
                            <td>
                                <strong>{row.model}</strong>
                                <span>{row.brand}</span>
                            </td>
                            <td>{row.marketListingCount}</td>
                            <td>{formatNullablePrice(row.medianMarketPrice)}</td>
                            <td>{row.purchasedCount}</td>
                            <td>{formatNullablePrice(row.averagePurchasePrice)}</td>
                            <td>{formatNullablePrice(row.medianPurchasePrice)}</td>
                            <td>{row.legitRejectedCount}</td>
                            <td>{row.missedOpportunityCount}</td>
                            <td>{row.scamCount}</td>
                            <td>
                                {formatNullableSignedPrice(
                                    row.purchaseBelowMarketMedianAmount,
                                )}
                                <span>
                                    {formatNullablePercent(
                                        row.purchaseBelowMarketMedianPercent,
                                    )}
                                </span>
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

function EmptyChart({
    text,
}: {
    text: string;
}) {
    return (
        <div className="analytics-empty-chart">
            {text}
        </div>
    );
}

function formatNullablePrice(value: number | null): string {
    return value === null ? "—" : formatPrice(value);
}

function formatNullableSignedPrice(value: number | null): string {
    if (value === null) {
        return "—";
    }

    return `${value > 0 ? "+" : ""}${formatPrice(value)}`;
}

function formatNullablePercent(value: number | null): string {
    if (value === null) {
        return "Brak porównania";
    }

    return `${value > 0 ? "+" : ""}${formatNumber(value, 1)}%`;
}

function formatPrice(value: number): string {
    return new Intl.NumberFormat("pl-PL", {
        style: "currency",
        currency: "PLN",
        maximumFractionDigits: 2,
    }).format(value);
}

function formatCompactPrice(value: number): string {
    return new Intl.NumberFormat("pl-PL", {
        maximumFractionDigits: 0,
    }).format(value);
}

function formatNumber(value: number, digits: number): string {
    return new Intl.NumberFormat("pl-PL", {
        minimumFractionDigits: digits,
        maximumFractionDigits: digits,
    }).format(value);
}

function formatDate(value: string): string {
    const date = new Date(`${value}T00:00:00`);

    return Number.isNaN(date.getTime())
        ? value
        : new Intl.DateTimeFormat("pl-PL", {
            day: "2-digit",
            month: "2-digit",
        }).format(date);
}

export default StatisticsPage;

import {
    useCallback,
    useEffect,
    useMemo,
    useState,
} from "react";

import {
    getAnalyticsOverview,
    type AnalyticsGranularity,
    type AnalyticsHistogramBucket,
    type AnalyticsModelBreakdown,
    type AnalyticsModelOption,
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

const periodOptions: Array<{ value: DashboardPeriod; label: string }> = [
    { value: "TODAY", label: "Dzisiaj" },
    { value: "LAST_7_DAYS", label: "7 dni" },
    { value: "LAST_30_DAYS", label: "30 dni" },
    { value: "THIS_MONTH", label: "Ten miesiąc" },
    { value: "THIS_YEAR", label: "Ten rok" },
    { value: "ALL", label: "Całość" },
];

const sourceOptions: AppSelectOption[] = [
    { value: "ALL", label: "Rynek + nasze decyzje" },
    { value: "OBSERVER", label: "Tylko rynek (Observer)" },
    { value: "HISTORY", label: "Tylko nasze decyzje" },
];

const granularityOptions: AppSelectOption[] = [
    { value: "DAY", label: "Dzień" },
    { value: "WEEK", label: "Tydzień" },
    { value: "MONTH", label: "Miesiąc" },
    { value: "YEAR", label: "Rok" },
];

const outcomeOptions: Array<{ value: HistoryOutcome; label: string }> = [
    { value: "PURCHASED", label: "Kupiłem" },
    { value: "REJECTED", label: "Nie kupiłem" },
    { value: "UNCLASSIFIED", label: "Do oznaczenia" },
];

const assessmentOptions: Array<{ value: OfferAssessment; label: string }> = [
    { value: "LEGIT", label: "Legit" },
    { value: "SCAM", label: "Oszustwo" },
    { value: "UNASSESSED", label: "Nieocenione" },
];

type PriceSeries =
    | "MARKET_MEDIAN"
    | "MARKET_AVERAGE"
    | "PURCHASE_AVERAGE"
    | "PURCHASE_MEDIAN";

const priceSeriesOptions: Array<{ value: PriceSeries; label: string }> = [
    { value: "MARKET_MEDIAN", label: "Mediana rynku" },
    { value: "MARKET_AVERAGE", label: "Średnia rynku" },
    { value: "PURCHASE_AVERAGE", label: "Średnia zakupu" },
    { value: "PURCHASE_MEDIAN", label: "Mediana zakupu" },
];

function StatisticsPage() {
    const [overview, setOverview] = useState<AnalyticsOverview | null>(null);
    const [period, setPeriod] = useState<DashboardPeriod>("THIS_MONTH");
    const [modelIds, setModelIds] = useState<number[]>([]);
    const [source, setSource] = useState<AnalyticsSource>("ALL");
    const [granularity, setGranularity] =
        useState<AnalyticsGranularity>("DAY");
    const [outcomes, setOutcomes] = useState<HistoryOutcome[]>([]);
    const [assessments, setAssessments] = useState<OfferAssessment[]>([]);
    const [from, setFrom] = useState("");
    const [to, setTo] = useState("");
    const [priceSeries, setPriceSeries] = useState<PriceSeries[]>([
        "MARKET_MEDIAN",
        "PURCHASE_AVERAGE",
    ]);
    const [isLoading, setIsLoading] = useState(true);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const load = useCallback(async () => {
        setIsLoading(true);
        setErrorMessage(null);

        try {
            setOverview(
                await getAnalyticsOverview({
                    period,
                    modelIds,
                    outcomes,
                    assessments,
                    source,
                    granularity,
                    from: from.length > 0 ? from : null,
                    to: to.length > 0 ? to : null,
                }),
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
    }, [
        period,
        modelIds,
        outcomes,
        assessments,
        source,
        granularity,
        from,
        to,
    ]);

    useEffect(() => {
        void load();
    }, [load]);

    const models = overview?.models ?? [];
    const summary = overview?.summary ?? null;

    function choosePreset(nextPeriod: DashboardPeriod) {
        setFrom("");
        setTo("");
        setPeriod(nextPeriod);
    }

    return (
        <section className="page analytics-page">
            <header className="page-header">
                <div>
                    <p className="page-eyebrow">Analiza rynku i zakupów</p>
                    <h1 className="page-title">Statystyki</h1>
                    <p className="page-description">
                        Rynek pochodzi z Observera. Nasze wyniki obejmują wyłącznie
                        oferty, które wcześniej trafiły do „Ofert do kupienia”.
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
                <div className="analytics-filter-row analytics-filter-row-main">
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
                    <div className="analytics-filter-field">
                        <label>Grupuj wykres po</label>
                        <AppSelect
                            value={granularity}
                            options={granularityOptions}
                            disabled={isLoading}
                            ariaLabel="Grupowanie wykresu"
                            onChange={value =>
                                setGranularity(value as AnalyticsGranularity)}
                        />
                    </div>
                    <div className="analytics-date-field">
                        <label>Od</label>
                        <input
                            className="form-input"
                            type="date"
                            value={from}
                            disabled={isLoading}
                            onChange={event => setFrom(event.target.value)}
                        />
                    </div>
                    <div className="analytics-date-field">
                        <label>Do</label>
                        <input
                            className="form-input"
                            type="date"
                            value={to}
                            disabled={isLoading}
                            onChange={event => setTo(event.target.value)}
                        />
                    </div>
                </div>

                <FilterPills
                    title="Szybki okres"
                    allMode={false}
                    options={periodOptions}
                    selected={[period]}
                    disabled={isLoading}
                    onSingleChange={choosePreset}
                />

                <ModelPicker
                    models={models}
                    selectedIds={modelIds}
                    disabled={isLoading}
                    onChange={setModelIds}
                />

                <FilterPills
                    title="Wynik naszych ofert"
                    allLabel="Wszystkie wyniki"
                    options={outcomeOptions}
                    selected={outcomes}
                    disabled={isLoading}
                    onChange={setOutcomes}
                />

                <FilterPills
                    title="Ocena naszych ofert"
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
                    <section className="analytics-section">
                        <div className="analytics-section-heading">
                            <div>
                                <span>Rynek / Observer</span>
                                <h2>Co dzieje się z wybranymi modelami</h2>
                            </div>
                        </div>

                        <div className="analytics-kpi-grid">
                            <AnalyticsKpi
                                label="Oferty rynku"
                                value={String(summary.marketListingCount)}
                                detail={`${summary.marketPriceSampleCount} obserwacji ma zapisaną cenę`}
                            />
                            <AnalyticsKpi
                                label="Średnia cena rynku"
                                value={formatNullablePrice(summary.averageMarketPrice)}
                                detail="Cena pierwszej zapisanej obserwacji"
                            />
                            <AnalyticsKpi
                                label="Mediana rynku"
                                value={formatNullablePrice(summary.medianMarketPrice)}
                                detail="Odporna na pojedyncze skrajne ceny"
                            />
                            <AnalyticsKpi
                                label="Średnio / dzień"
                                value={formatNullableRate(summary.averageListingsPerDay)}
                                detail="Nowe oferty w wybranym okresie"
                            />
                            <AnalyticsKpi
                                label="Średnio / tydzień"
                                value={formatNullableRate(summary.averageListingsPerWeek)}
                                detail="Nowe oferty w wybranym okresie"
                            />
                            <AnalyticsKpi
                                label="Średnio / miesiąc"
                                value={formatNullableRate(summary.averageListingsPerMonth)}
                                detail="Nowe oferty w wybranym okresie"
                            />
                        </div>
                    </section>

                    <section className="analytics-section">
                        <div className="analytics-section-heading">
                            <div>
                                <span>Nasze decyzje</span>
                                <h2>Tylko oferty, które były „do kupienia”</h2>
                            </div>
                        </div>

                        <div className="analytics-kpi-grid">
                            <AnalyticsKpi
                                label="Kupione"
                                value={String(summary.purchasedCount)}
                                detail="Oznaczone jako Kupiłem"
                            />
                            <AnalyticsKpi
                                label="Średnia cena zakupu"
                                value={formatNullablePrice(summary.averagePurchasePrice)}
                                detail="Cena końcowa kupionych"
                            />
                            <AnalyticsKpi
                                label="Mediana zakupu"
                                value={formatNullablePrice(summary.medianPurchasePrice)}
                                detail="Mediana naszych cen zakupu"
                            />
                            <AnalyticsKpi
                                label="Nie kupiłem"
                                value={String(summary.rejectedCount)}
                                detail={`${summary.missedOpportunityCount} z powodu utraconej okazji`}
                            />
                            <AnalyticsKpi
                                label="Oszustwa"
                                value={String(summary.scamCount)}
                                detail="Oferty oznaczone ręcznie jako oszustwo"
                            />
                            <AnalyticsKpi
                                label="Zakup vs mediana rynku"
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
                        </div>
                    </section>

                    <div className="analytics-grid analytics-grid-two">
                        <article className="content-card analytics-chart-card">
                            <div className="analytics-card-heading analytics-card-heading-stacked">
                                <div>
                                    <h2 className="content-card-title">
                                        Ceny w czasie
                                    </h2>
                                    <p className="content-card-text">
                                        Włącz dokładnie te serie, które chcesz porównywać.
                                    </p>
                                </div>
                                <FilterPills
                                    title="Serie wykresu"
                                    allLabel="Wszystkie"
                                    options={priceSeriesOptions}
                                    selected={priceSeries}
                                    disabled={false}
                                    onChange={setPriceSeries}
                                />
                            </div>
                            <PriceTimelineChart
                                points={overview.timeline}
                                selectedSeries={priceSeries}
                            />
                        </article>

                        <article className="content-card analytics-chart-card">
                            <div className="analytics-card-heading">
                                <div>
                                    <h2 className="content-card-title">
                                        Liczba nowych ofert
                                    </h2>
                                    <p className="content-card-text">
                                        Tempo pojawiania się ofert w wybranym grupowaniu.
                                    </p>
                                </div>
                            </div>
                            <VolumeChart points={overview.timeline} />
                        </article>
                    </div>

                    <div className="analytics-grid analytics-grid-two">
                        <article className="content-card analytics-chart-card">
                            <div className="analytics-card-heading">
                                <div>
                                    <h2 className="content-card-title">
                                        Rozkład cen rynku
                                    </h2>
                                    <p className="content-card-text">
                                        Gdzie skupiają się ceny wybranych modeli.
                                    </p>
                                </div>
                            </div>
                            <HistogramChart buckets={overview.marketPriceHistogram} />
                        </article>

                        <article className="content-card analytics-descriptive-card">
                            <h2 className="content-card-title">
                                Statystyki opisowe rynku
                            </h2>
                            <div className="analytics-descriptive-grid analytics-descriptive-grid-compact">
                                <Metric label="Minimum" value={formatNullablePrice(summary.marketMinPrice)} />
                                <Metric label="P25" value={formatNullablePrice(summary.marketP25)} />
                                <Metric label="Mediana" value={formatNullablePrice(summary.medianMarketPrice)} />
                                <Metric label="P75" value={formatNullablePrice(summary.marketP75)} />
                                <Metric label="Maksimum" value={formatNullablePrice(summary.marketMaxPrice)} />
                                <Metric
                                    label="Odchylenie"
                                    value={formatNullablePrice(summary.marketStandardDeviation)}
                                />
                            </div>
                        </article>
                    </div>

                    <article className="content-card analytics-table-card">
                        <div className="analytics-card-heading">
                            <div>
                                <h2 className="content-card-title">
                                    Modele
                                </h2>
                                <p className="content-card-text">
                                    Ceny, tempo rynku i nasze zakupy per model.
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

function ModelPicker({
    models,
    selectedIds,
    disabled,
    onChange,
}: {
    models: AnalyticsModelOption[];
    selectedIds: number[];
    disabled: boolean;
    onChange: (ids: number[]) => void;
}) {
    const [query, setQuery] = useState("");

    const visibleModels = useMemo(() => {
        const normalized = query.trim().toLowerCase();

        if (normalized.length === 0) {
            return models;
        }

        return models.filter(model =>
            `${model.brand} ${model.model}`.toLowerCase().includes(normalized),
        );
    }, [models, query]);

    function toggle(id: number) {
        onChange(
            selectedIds.includes(id)
                ? selectedIds.filter(value => value !== id)
                : [...selectedIds, id],
        );
    }

    return (
        <div className="analytics-model-picker">
            <div className="analytics-model-picker-heading">
                <div>
                    <span>Modele</span>
                    <strong>
                        {selectedIds.length === 0
                            ? "Wszystkie modele"
                            : `Wybrano: ${selectedIds.length}`}
                    </strong>
                </div>
                {selectedIds.length > 0 && (
                    <button
                        className="analytics-clear-models"
                        type="button"
                        disabled={disabled}
                        onClick={() => onChange([])}
                    >
                        Wyczyść
                    </button>
                )}
            </div>

            <input
                className="form-input analytics-model-search"
                type="search"
                placeholder="Szukaj modelu, np. S26 albo iPad..."
                value={query}
                disabled={disabled}
                onChange={event => setQuery(event.target.value)}
            />

            <div className="analytics-model-options">
                {visibleModels.map(model => {
                    const active = selectedIds.includes(model.modelId);

                    return (
                        <button
                            key={model.modelId}
                            className={
                                active
                                    ? "analytics-model-option analytics-model-option-active"
                                    : "analytics-model-option"
                            }
                            type="button"
                            disabled={disabled}
                            onClick={() => toggle(model.modelId)}
                        >
                            <span>{model.brand}</span>
                            <strong>{model.model}</strong>
                        </button>
                    );
                })}
            </div>
        </div>
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
    selectedSeries,
}: {
    points: AnalyticsTimelinePoint[];
    selectedSeries: PriceSeries[];
}) {
    const series = [
        {
            id: "MARKET_MEDIAN" as const,
            label: "Mediana rynku",
            className: "analytics-line-market",
            dotClass: "analytics-dot-market",
            value: (point: AnalyticsTimelinePoint) => point.medianMarketPrice,
        },
        {
            id: "MARKET_AVERAGE" as const,
            label: "Średnia rynku",
            className: "analytics-line-market-average",
            dotClass: "analytics-dot-market-average",
            value: (point: AnalyticsTimelinePoint) => point.averageMarketPrice,
        },
        {
            id: "PURCHASE_AVERAGE" as const,
            label: "Średnia zakupu",
            className: "analytics-line-purchase",
            dotClass: "analytics-dot-purchase",
            value: (point: AnalyticsTimelinePoint) => point.averagePurchasePrice,
        },
        {
            id: "PURCHASE_MEDIAN" as const,
            label: "Mediana zakupu",
            className: "analytics-line-purchase-median",
            dotClass: "analytics-dot-purchase-median",
            value: (point: AnalyticsTimelinePoint) => point.medianPurchasePrice,
        },
    ].filter(item =>
        selectedSeries.length === 0
        || selectedSeries.includes(item.id),
    );

    const usableValues = points.flatMap(point =>
        series.map(item => item.value(point)),
    ).filter((value): value is number => value !== null);

    if (points.length === 0 || usableValues.length === 0 || series.length === 0) {
        return <EmptyChart text="Brak danych dla wybranych serii i filtrów." />;
    }

    const min = Math.min(...usableValues);
    const max = Math.max(...usableValues);
    const range = Math.max(max - min, 1);
    const width = 900;
    const height = 280;
    const paddingX = 48;
    const paddingY = 28;
    const chartWidth = width - paddingX * 2;
    const chartHeight = height - paddingY * 2;

    const x = (index: number) =>
        paddingX + (
            points.length === 1
                ? chartWidth / 2
                : (index / (points.length - 1)) * chartWidth
        );
    const y = (value: number) =>
        paddingY + chartHeight - ((value - min) / range) * chartHeight;

    return (
        <div className="analytics-timeline">
            <div className="analytics-legend">
                {series.map(item => (
                    <span
                        key={item.id}
                        className={`analytics-legend-series analytics-legend-${item.id.toLowerCase()}`}
                    >
                        {item.label}
                    </span>
                ))}
            </div>

            <svg viewBox={`0 0 ${width} ${height}`} role="img" aria-label="Wykres cen w czasie">
                <line className="analytics-axis" x1={paddingX} x2={paddingX} y1={paddingY} y2={height - paddingY} />
                <line className="analytics-axis" x1={paddingX} x2={width - paddingX} y1={height - paddingY} y2={height - paddingY} />

                {series.map(item => {
                    const path = buildSvgPath(points, item.value, x, y);

                    return (
                        <g key={item.id}>
                            {path.length > 0 && (
                                <path
                                    className={`analytics-line ${item.className}`}
                                    d={path}
                                />
                            )}
                            {points.map((point, index) => {
                                const value = item.value(point);

                                return value === null ? null : (
                                    <circle
                                        key={`${item.id}-${point.date}`}
                                        className={`analytics-dot ${item.dotClass}`}
                                        cx={x(index)}
                                        cy={y(value)}
                                        r="3.5"
                                    />
                                );
                            })}
                        </g>
                    );
                })}
            </svg>

            <div className="analytics-chart-footer">
                <span>{points[0]?.label ?? ""}</span>
                <span>{formatPrice(max)} – {formatPrice(min)}</span>
                <span>{points[points.length - 1]?.label ?? ""}</span>
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

function VolumeChart({
    points,
}: {
    points: AnalyticsTimelinePoint[];
}) {
    if (points.length === 0) {
        return <EmptyChart text="Brak obserwacji rynku w tym okresie." />;
    }

    const maxCount = Math.max(
        ...points.map(point => point.marketListingCount),
        1,
    );

    return (
        <div className="analytics-volume">
            <div className="analytics-volume-bars">
                {points.map(point => (
                    <div
                        className="analytics-volume-column"
                        key={point.date}
                        title={`${point.label}: ${point.marketListingCount} ofert`}
                    >
                        <span>{point.marketListingCount}</span>
                        <div
                            className="analytics-volume-bar"
                            style={{
                                height: `${Math.max(
                                    6,
                                    (point.marketListingCount / maxCount) * 180,
                                )}px`,
                            }}
                        />
                        <small>{point.label}</small>
                    </div>
                ))}
            </div>
        </div>
    );
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
                        <th>Oferty</th>
                        <th>Śr. rynek</th>
                        <th>Mediana rynku</th>
                        <th>/ dzień</th>
                        <th>/ tydzień</th>
                        <th>/ miesiąc</th>
                        <th>Kupione</th>
                        <th>Śr. zakup</th>
                        <th>Mediana zakupu</th>
                        <th>Nie kupiłem legit</th>
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
                            <td>{formatNullablePrice(row.averageMarketPrice)}</td>
                            <td>{formatNullablePrice(row.medianMarketPrice)}</td>
                            <td>{formatNullableRate(row.averageListingsPerDay)}</td>
                            <td>{formatNullableRate(row.averageListingsPerWeek)}</td>
                            <td>{formatNullableRate(row.averageListingsPerMonth)}</td>
                            <td>{row.purchasedCount}</td>
                            <td>{formatNullablePrice(row.averagePurchasePrice)}</td>
                            <td>{formatNullablePrice(row.medianPurchasePrice)}</td>
                            <td>{row.legitRejectedCount}</td>
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

function EmptyChart({ text }: { text: string }) {
    return <div className="analytics-empty-chart">{text}</div>;
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

function formatNullableRate(value: number | null): string {
    return value === null
        ? "—"
        : formatNumber(value, value < 10 ? 2 : 1);
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

export default StatisticsPage;
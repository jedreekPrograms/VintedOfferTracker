import {
    assertApiResponse,
} from "./apiError";
import type {
    DashboardPeriod,
} from "./dashboardApi";
import type {
    HistoryOutcome,
    OfferAssessment,
} from "./historyApi";

export type AnalyticsSource =
    | "ALL"
    | "HISTORY"
    | "OBSERVER";

export type AnalyticsGranularity =
    | "DAY"
    | "WEEK"
    | "MONTH"
    | "YEAR";

export interface AnalyticsModelOption {
    modelId: number;
    brand: string;
    model: string;
    label: string;
}

export interface AnalyticsSummary {
    historyCount: number;
    purchasedCount: number;
    rejectedCount: number;
    missedOpportunityCount: number;
    legitCount: number;
    scamCount: number;
    unassessedCount: number;
    marketListingCount: number;
    marketPriceSampleCount: number;
    averageMarketPrice: number | null;
    medianMarketPrice: number | null;
    marketP25: number | null;
    marketP75: number | null;
    marketStandardDeviation: number | null;
    marketMinPrice: number | null;
    marketMaxPrice: number | null;
    averageListingsPerDay: number | null;
    averageListingsPerWeek: number | null;
    averageListingsPerMonth: number | null;
    averagePurchasePrice: number | null;
    medianPurchasePrice: number | null;
    averageLegitRejectedPrice: number | null;
    medianLegitRejectedPrice: number | null;
    averageMissedOpportunityPrice: number | null;
    medianMissedOpportunityPrice: number | null;
    purchaseBelowMarketMedianAmount: number | null;
    purchaseBelowMarketMedianPercent: number | null;
}

export interface AnalyticsModelBreakdown {
    modelId: number;
    brand: string;
    model: string;
    label: string;
    marketListingCount: number;
    marketPriceSampleCount: number;
    averageMarketPrice: number | null;
    medianMarketPrice: number | null;
    averageListingsPerDay: number | null;
    averageListingsPerWeek: number | null;
    averageListingsPerMonth: number | null;
    purchasedCount: number;
    averagePurchasePrice: number | null;
    medianPurchasePrice: number | null;
    legitRejectedCount: number;
    missedOpportunityCount: number;
    scamCount: number;
    purchaseBelowMarketMedianAmount: number | null;
    purchaseBelowMarketMedianPercent: number | null;
}

export interface AnalyticsTimelinePoint {
    date: string;
    label: string;
    marketListingCount: number;
    averageMarketPrice: number | null;
    medianMarketPrice: number | null;
    purchaseCount: number;
    averagePurchasePrice: number | null;
    medianPurchasePrice: number | null;
}

export interface AnalyticsHistogramBucket {
    from: number;
    to: number;
    count: number;
}

export interface AnalyticsOverview {
    models: AnalyticsModelOption[];
    summary: AnalyticsSummary;
    modelBreakdowns: AnalyticsModelBreakdown[];
    timeline: AnalyticsTimelinePoint[];
    marketPriceHistogram: AnalyticsHistogramBucket[];
}

export interface AnalyticsQuery {
    period: DashboardPeriod;
    modelIds: number[];
    outcomes: HistoryOutcome[];
    assessments: OfferAssessment[];
    source: AnalyticsSource;
    granularity: AnalyticsGranularity;
    from: string | null;
    to: string | null;
}

export async function getAnalyticsOverview(
    query: AnalyticsQuery,
): Promise<AnalyticsOverview> {
    const params = new URLSearchParams();
    params.set("period", query.period);
    params.set("source", query.source);
    params.set("granularity", query.granularity);

    for (const modelId of query.modelIds) {
        params.append("modelIds", String(modelId));
    }

    for (const outcome of query.outcomes) {
        params.append("outcomes", outcome);
    }

    for (const assessment of query.assessments) {
        params.append("assessments", assessment);
    }

    if (query.from !== null && query.from.length > 0) {
        params.set("from", query.from);
    }

    if (query.to !== null && query.to.length > 0) {
        params.set("to", query.to);
    }

    const response = await fetch(
        `/api/analytics/overview?${params.toString()}`,
    );

    await assertApiResponse(
        response,
        `Nie udało się pobrać statystyk. HTTP ${response.status}`,
    );

    return response.json() as Promise<AnalyticsOverview>;
}

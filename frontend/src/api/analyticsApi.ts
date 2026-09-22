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
    marketListingCount: number;
    averageMarketPrice: number | null;
    medianMarketPrice: number | null;
    purchaseCount: number;
    averagePurchasePrice: number | null;
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

export async function getAnalyticsOverview(
    period: DashboardPeriod,
    modelId: number | null,
    outcomes: HistoryOutcome[],
    assessments: OfferAssessment[],
    source: AnalyticsSource,
): Promise<AnalyticsOverview> {
    const params = new URLSearchParams();
    params.set("period", period);
    params.set("source", source);

    if (modelId !== null) {
        params.set("modelId", String(modelId));
    }

    for (const outcome of outcomes) {
        params.append("outcomes", outcome);
    }

    for (const assessment of assessments) {
        params.append("assessments", assessment);
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

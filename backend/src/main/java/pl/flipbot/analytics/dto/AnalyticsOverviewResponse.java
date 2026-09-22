package pl.flipbot.analytics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AnalyticsOverviewResponse(
        List<ModelOption> models,
        Summary summary,
        List<ModelBreakdown> modelBreakdowns,
        List<TimelinePoint> timeline,
        List<HistogramBucket> marketPriceHistogram
) {
    public record ModelOption(
            Long modelId,
            String brand,
            String model,
            String label
    ) {
    }

    public record Summary(
            long historyCount,
            long purchasedCount,
            long rejectedCount,
            long missedOpportunityCount,
            long legitCount,
            long scamCount,
            long unassessedCount,
            long marketListingCount,
            int marketPriceSampleCount,
            BigDecimal averageMarketPrice,
            BigDecimal medianMarketPrice,
            BigDecimal marketP25,
            BigDecimal marketP75,
            BigDecimal marketStandardDeviation,
            BigDecimal marketMinPrice,
            BigDecimal marketMaxPrice,
            BigDecimal averageListingsPerDay,
            BigDecimal averageListingsPerWeek,
            BigDecimal averageListingsPerMonth,
            BigDecimal averagePurchasePrice,
            BigDecimal medianPurchasePrice,
            BigDecimal averageLegitRejectedPrice,
            BigDecimal medianLegitRejectedPrice,
            BigDecimal averageMissedOpportunityPrice,
            BigDecimal medianMissedOpportunityPrice,
            BigDecimal purchaseBelowMarketMedianAmount,
            BigDecimal purchaseBelowMarketMedianPercent
    ) {
    }

    public record ModelBreakdown(
            Long modelId,
            String brand,
            String model,
            String label,
            long marketListingCount,
            int marketPriceSampleCount,
            BigDecimal averageMarketPrice,
            BigDecimal medianMarketPrice,
            BigDecimal averageListingsPerDay,
            BigDecimal averageListingsPerWeek,
            BigDecimal averageListingsPerMonth,
            long purchasedCount,
            BigDecimal averagePurchasePrice,
            BigDecimal medianPurchasePrice,
            long legitRejectedCount,
            long missedOpportunityCount,
            long scamCount,
            BigDecimal purchaseBelowMarketMedianAmount,
            BigDecimal purchaseBelowMarketMedianPercent
    ) {
    }

    public record TimelinePoint(
            LocalDate date,
            String label,
            long marketListingCount,
            BigDecimal averageMarketPrice,
            BigDecimal medianMarketPrice,
            long purchaseCount,
            BigDecimal averagePurchasePrice,
            BigDecimal medianPurchasePrice
    ) {
    }

    public record HistogramBucket(
            BigDecimal from,
            BigDecimal to,
            int count
    ) {
    }
}

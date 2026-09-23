package pl.flipbot.dashboard.dto;

import java.math.BigDecimal;

public record DashboardStatsResponse(

        long activeBotsCount,

        long negotiatingCount,

        long actionRequiredCount,

        long purchasedCount,

        long skippedByUserCount,

        BigDecimal totalSpent,

        BigDecimal totalNegotiatedSavings,

        BigDecimal averagePurchasePrice,

        BigDecimal averageDiscountPercentage,

        long selectedHistoryCount,

        long missedOpportunityCount,

        long legitCount,

        long scamCount,

        long unassessedCount,

        BigDecimal averageSelectedPrice,

        BigDecimal medianSelectedPrice

) {
}

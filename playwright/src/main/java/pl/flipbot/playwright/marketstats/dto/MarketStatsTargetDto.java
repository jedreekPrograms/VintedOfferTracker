package pl.flipbot.playwright.marketstats.dto;

import java.math.BigDecimal;
import java.util.List;

public record MarketStatsTargetDto(
        Long modelId,
        String brandName,
        String modelName,
        String targetMode,
        List<String> categoryPath,
        boolean categoryResolved,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}

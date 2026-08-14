package pl.flipbot.playwright.marketstats.dto;

import java.util.List;

public record MarketStatsTargetDto(
        Long modelId,
        String brandName,
        String modelName,
        String targetMode,
        List<String> categoryPath,
        boolean categoryResolved
) {
}

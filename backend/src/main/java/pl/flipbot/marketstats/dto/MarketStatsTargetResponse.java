package pl.flipbot.marketstats.dto;

import pl.flipbot.bot.configuration.TargetMode;

import java.util.List;

public record MarketStatsTargetResponse(
        Long modelId,
        String brandName,
        String modelName,
        TargetMode targetMode,
        List<String> categoryPath,
        boolean categoryResolved
) {
}

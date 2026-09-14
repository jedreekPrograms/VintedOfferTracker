package pl.flipbot.listing;

import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.TargetMode;

final class ListingProductProvenance {

    private ListingProductProvenance() {
    }

    static String label(
            Bot bot,
            BotAdditionalTarget additionalTarget
    ) {
        if (additionalTarget != null) {
            return label(
                    additionalTarget.getBrand(),
                    additionalTarget.getTargetMode(),
                    additionalTarget.getModel(),
                    additionalTarget.getSearchQuery()
            );
        }

        BotConfiguration configuration = bot == null
                ? null
                : bot.getConfiguration();

        if (configuration == null) {
            return null;
        }

        return label(
                configuration.getBrand(),
                configuration.getTargetMode(),
                configuration.getModel(),
                configuration.getSearchQuery()
        );
    }

    private static String label(
            String brand,
            TargetMode targetMode,
            String model,
            String searchQuery
    ) {
        String normalizedBrand = normalize(brand);
        String target = targetMode == TargetMode.SEARCH_QUERY
                ? normalize(searchQuery)
                : normalize(model);

        if (normalizedBrand == null) {
            return target;
        }
        if (target == null) {
            return normalizedBrand;
        }
        return normalizedBrand + " → " + target;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}

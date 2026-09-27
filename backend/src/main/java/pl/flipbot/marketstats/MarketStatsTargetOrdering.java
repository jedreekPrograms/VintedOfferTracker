package pl.flipbot.marketstats;

import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.dictionary.DictionaryModel;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class MarketStatsTargetOrdering {

    private static final String CATEGORY_PATH_SEPARATOR_REGEX = "\\s*>\\s*";

    private MarketStatsTargetOrdering() {
    }

    static Comparator<DictionaryModel> comparator(
            List<BotConfiguration> configurations
    ) {
        List<BotConfiguration> safeConfigurations =
                configurations == null
                        ? List.of()
                        : configurations;

        return Comparator
                .comparingInt(
                        (DictionaryModel model) ->
                                priority(model, safeConfigurations)
                )
                .thenComparing(
                        model -> model.getBrand().getName(),
                        String.CASE_INSENSITIVE_ORDER
                )
                .thenComparing(
                        DictionaryModel::getName,
                        String.CASE_INSENSITIVE_ORDER
                );
    }

    static int priority(
            DictionaryModel model,
            List<BotConfiguration> configurations
    ) {
        if (model == null) {
            return 3;
        }

        List<BotConfiguration> relevant = configurations == null
                ? List.of()
                : configurations.stream()
                .filter(MarketStatsTargetOrdering::isRelevantRunningBot)
                .toList();

        if (relevant.stream().anyMatch(
                configuration -> exactModelMatch(model, configuration)
        )) {
            return 0;
        }

        if (relevant.stream().anyMatch(
                configuration -> sameMarketSegment(model, configuration)
        )) {
            return 1;
        }

        if (relevant.stream().anyMatch(
                configuration -> sameText(
                        model.getBrand().getName(),
                        configuration.getBrand()
                )
        )) {
            return 2;
        }

        return 3;
    }

    private static boolean isRelevantRunningBot(
            BotConfiguration configuration
    ) {
        return configuration != null
                && configuration.getBot() != null
                && configuration.getBot().getStatus() == BotStatus.RUNNING
                && !Boolean.TRUE.equals(
                configuration.getBot().getMarketStatsObserver()
        );
    }

    private static boolean exactModelMatch(
            DictionaryModel model,
            BotConfiguration configuration
    ) {
        if (!sameText(
                model.getBrand().getName(),
                configuration.getBrand()
        )) {
            return false;
        }

        TargetMode modelMode = model.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : model.getTargetMode();

        TargetMode configurationMode =
                configuration.getTargetMode() == null
                        ? TargetMode.VINTED_MODEL
                        : configuration.getTargetMode();

        if (modelMode != configurationMode) {
            return false;
        }

        return switch (modelMode) {
            case VINTED_MODEL -> sameText(
                    model.getName(),
                    configuration.getModel()
            );
            case SEARCH_QUERY -> sameText(
                    model.getName(),
                    configuration.getSearchQuery()
            );
        };
    }

    private static boolean sameMarketSegment(
            DictionaryModel model,
            BotConfiguration configuration
    ) {
        if (!sameText(
                model.getBrand().getName(),
                configuration.getBrand()
        )) {
            return false;
        }

        List<String> modelCategoryPath = dictionaryCategoryPath(model);
        List<String> configuredPath = configuration.getCategoryPath();

        if (modelCategoryPath.isEmpty()
                || configuredPath == null
                || configuredPath.isEmpty()
                || modelCategoryPath.size() != configuredPath.size()) {
            return false;
        }

        for (int index = 0; index < modelCategoryPath.size(); index++) {
            if (!sameText(
                    modelCategoryPath.get(index),
                    configuredPath.get(index)
            )) {
                return false;
            }
        }

        return true;
    }

    private static List<String> dictionaryCategoryPath(
            DictionaryModel model
    ) {
        if (model == null
                || model.getCategory() == null
                || model.getCategory().getPath() == null
                || model.getCategory().getPath().isBlank()) {
            return List.of();
        }

        return Arrays.stream(
                        model.getCategory()
                                .getPath()
                                .split(CATEGORY_PATH_SEPARATOR_REGEX)
                )
                .map(String::trim)
                .filter(element -> !element.isBlank())
                .toList();
    }

    private static boolean sameText(
            String left,
            String right
    ) {
        if (left == null || right == null) {
            return false;
        }

        return normalize(left).equals(normalize(right));
    }

    private static String normalize(String value) {
        return value
                .trim()
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }
}

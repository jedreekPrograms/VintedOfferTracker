package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public record ScheduledActionLimitConfig(
        int maxRealOffersPerCatalogScan,
        int maxRealNextStepsPerCheck
) {

    static final String MAX_REAL_OFFERS_ENV =
            "FLIPBOT_MAX_REAL_OFFERS_PER_CATALOG_SCAN";

    static final String MAX_REAL_NEXT_STEPS_ENV =
            "FLIPBOT_MAX_REAL_NEXT_STEPS_PER_CHECK";

    private static final int DEFAULT_MAX_REAL_OFFERS_PER_CATALOG_SCAN = 3;
    private static final int DEFAULT_MAX_REAL_NEXT_STEPS_PER_CHECK = 1;
    private static final int MIN_ACTION_LIMIT = 1;
    private static final int MAX_ACTION_LIMIT = 5;

    public static ScheduledActionLimitConfig fromEnvironment() {
        return new ScheduledActionLimitConfig(
                parseLimit(
                        MAX_REAL_OFFERS_ENV,
                        System.getenv(MAX_REAL_OFFERS_ENV),
                        DEFAULT_MAX_REAL_OFFERS_PER_CATALOG_SCAN
                ),
                parseLimit(
                        MAX_REAL_NEXT_STEPS_ENV,
                        System.getenv(MAX_REAL_NEXT_STEPS_ENV),
                        DEFAULT_MAX_REAL_NEXT_STEPS_PER_CHECK
                )
        );
    }

    /**
     * Controlled real-action mode remains deliberately capped at one action.
     * Configurable throughput applies only after the dedicated production gate
     * is fully armed.
     */
    public int effectiveMaxRealOffers(boolean productionModeEnabled) {
        return productionModeEnabled
                ? maxRealOffersPerCatalogScan
                : 1;
    }

    public int effectiveMaxRealNextSteps(boolean productionModeEnabled) {
        return productionModeEnabled
                ? maxRealNextStepsPerCheck
                : 1;
    }

    static int parseLimit(
            String environmentName,
            String rawValue,
            int defaultValue
    ) {
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        try {
            int parsed = Integer.parseInt(rawValue.trim());

            if (parsed < MIN_ACTION_LIMIT || parsed > MAX_ACTION_LIMIT) {
                throw new IllegalArgumentException(
                        "Value is outside allowed range "
                                + MIN_ACTION_LIMIT
                                + ".."
                                + MAX_ACTION_LIMIT
                );
            }

            return parsed;

        } catch (RuntimeException exception) {
            log.warn(
                    "[ACTION LIMIT CONFIG] Invalid {}='{}'. Using default {}. Allowed range is {}..{}.",
                    environmentName,
                    rawValue,
                    defaultValue,
                    MIN_ACTION_LIMIT,
                    MAX_ACTION_LIMIT
            );
            return defaultValue;
        }
    }
}

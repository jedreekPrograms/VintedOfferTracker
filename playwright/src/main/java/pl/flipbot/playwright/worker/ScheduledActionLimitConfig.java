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

    /*
     * Production throughput is governed by backend capacity, daily quota and
     * persistent action guards. An additional hidden 3/1 per-run throttle made
     * valid work spill into later scheduler runs for no safety benefit.
     *
     * Environment values remain available as an explicit operator throttle;
     * when absent, production is effectively unbounded at this layer.
     */
    private static final int DEFAULT_PRODUCTION_LIMIT = Integer.MAX_VALUE;
    private static final int MIN_ACTION_LIMIT = 1;

    public static ScheduledActionLimitConfig fromEnvironment() {
        return new ScheduledActionLimitConfig(
                parseLimit(
                        MAX_REAL_OFFERS_ENV,
                        System.getenv(MAX_REAL_OFFERS_ENV),
                        DEFAULT_PRODUCTION_LIMIT
                ),
                parseLimit(
                        MAX_REAL_NEXT_STEPS_ENV,
                        System.getenv(MAX_REAL_NEXT_STEPS_ENV),
                        DEFAULT_PRODUCTION_LIMIT
                )
        );
    }

    /**
     * Controlled real-action mode remains deliberately capped at one action.
     * In fully armed production mode the backend planner/quota/guard chain is
     * authoritative unless the operator explicitly configured a lower limit.
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

            if (parsed < MIN_ACTION_LIMIT) {
                throw new IllegalArgumentException(
                        "Value must be at least " + MIN_ACTION_LIMIT
                );
            }

            return parsed;

        } catch (RuntimeException exception) {
            log.warn(
                    "[ACTION LIMIT CONFIG] Invalid {}='{}'. Using {}. Value must be a positive integer.",
                    environmentName,
                    rawValue,
                    defaultValue == Integer.MAX_VALUE ? "backend-controlled production throughput" : defaultValue
            );
            return defaultValue;
        }
    }
}

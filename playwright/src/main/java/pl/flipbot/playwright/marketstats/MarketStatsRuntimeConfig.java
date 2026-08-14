package pl.flipbot.playwright.marketstats;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public record MarketStatsRuntimeConfig(
        boolean enabled,
        Long observerBotId,
        boolean headless,
        long intervalHours,
        int maxListingsPerModel,
        int knownBoundarySize,
        long interModelDelayMillis
) {

    private static final String ENABLED_ENV =
            "FLIPBOT_MARKET_STATS_ENABLED";
    private static final String HEADLESS_ENV =
            "FLIPBOT_MARKET_STATS_HEADLESS";
    private static final String INTERVAL_HOURS_ENV =
            "FLIPBOT_MARKET_STATS_INTERVAL_HOURS";
    private static final String MAX_LISTINGS_ENV =
            "FLIPBOT_MARKET_STATS_MAX_LISTINGS_PER_MODEL";
    private static final String KNOWN_BOUNDARY_ENV =
            "FLIPBOT_MARKET_STATS_KNOWN_BOUNDARY";
    private static final String INTER_MODEL_DELAY_ENV =
            "FLIPBOT_MARKET_STATS_INTER_MODEL_DELAY_MS";

    public static MarketStatsRuntimeConfig fromEnvironment() {
        MarketStatsRuntimeConfig config =
                new MarketStatsRuntimeConfig(
                        readBoolean(ENABLED_ENV, true),
                        null,
                        readBoolean(HEADLESS_ENV, true),
                        readLongInRange(INTERVAL_HOURS_ENV, 24L, 1L, 168L),
                        readIntInRange(MAX_LISTINGS_ENV, 600, 50, 5_000),
                        readIntInRange(KNOWN_BOUNDARY_ENV, 20, 5, 100),
                        readLongInRange(INTER_MODEL_DELAY_ENV, 2_500L, 0L, 60_000L)
                );

        log.info(
                "[MARKET STATS CONFIG] enabled={}, observer=frontend-managed, headless={}, interval={}h, maxListingsPerModel={}, knownBoundary={}, interModelDelay={}ms.",
                config.enabled(),
                config.headless(),
                config.intervalHours(),
                config.maxListingsPerModel(),
                config.knownBoundarySize(),
                config.interModelDelayMillis()
        );

        return config;
    }

    private static boolean readBoolean(
            String name,
            boolean fallback
    ) {
        String raw = System.getenv(name);

        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        return Boolean.parseBoolean(raw.trim());
    }

    private static int readIntInRange(
            String name,
            int fallback,
            int minimum,
            int maximum
    ) {
        String raw = System.getenv(name);

        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static long readLongInRange(
            String name,
            long fallback,
            long minimum,
            long maximum
    ) {
        String raw = System.getenv(name);

        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            long value = Long.parseLong(raw.trim());
            return Math.max(minimum, Math.min(maximum, value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}

package pl.flipbot.playwright.marketstats;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

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
    private static final String SCHEDULER_HEADLESS_ENV =
            "FLIPBOT_SCHEDULER_HEADLESS";
    private static final String INTERVAL_HOURS_ENV =
            "FLIPBOT_MARKET_STATS_INTERVAL_HOURS";
    private static final String MAX_LISTINGS_ENV =
            "FLIPBOT_MARKET_STATS_MAX_LISTINGS_PER_MODEL";
    private static final String KNOWN_BOUNDARY_ENV =
            "FLIPBOT_MARKET_STATS_KNOWN_BOUNDARY";
    private static final String INTER_MODEL_DELAY_ENV =
            "FLIPBOT_MARKET_STATS_INTER_MODEL_DELAY_MS";

    public static MarketStatsRuntimeConfig fromEnvironment() {
        String explicitObserverHeadless = System.getenv(HEADLESS_ENV);
        String schedulerHeadless = System.getenv(SCHEDULER_HEADLESS_ENV);

        MarketStatsRuntimeConfig config =
                new MarketStatsRuntimeConfig(
                        readBoolean(ENABLED_ENV, true),
                        null,
                        resolveObserverHeadless(
                                explicitObserverHeadless,
                                schedulerHeadless
                        ),
                        readLongInRange(INTERVAL_HOURS_ENV, 24L, 1L, 168L),
                        readIntInRange(MAX_LISTINGS_ENV, 600, 50, 5_000),
                        readIntInRange(KNOWN_BOUNDARY_ENV, 20, 5, 100),
                        readLongInRange(INTER_MODEL_DELAY_ENV, 2_500L, 0L, 60_000L)
                );

        String headlessSource = hasText(explicitObserverHeadless)
                ? HEADLESS_ENV + " (explicit observer override)"
                : hasText(schedulerHeadless)
                ? SCHEDULER_HEADLESS_ENV + " (inherited from normal workers)"
                : "default=true";

        log.info(
                "[MARKET STATS CONFIG] enabled={}, observer=frontend-managed, headless={} [{}], interval={}h, maxListingsPerModel={}, knownBoundary={}, interModelDelay={}ms.",
                config.enabled(),
                config.headless(),
                headlessSource,
                config.intervalHours(),
                config.maxListingsPerModel(),
                config.knownBoundarySize(),
                config.interModelDelayMillis()
        );

        return config;
    }

    static boolean resolveObserverHeadless(
            String explicitObserverHeadless,
            String schedulerHeadless
    ) {
        if (hasText(explicitObserverHeadless)) {
            return parseBoolean(
                    HEADLESS_ENV,
                    explicitObserverHeadless,
                    true
            );
        }

        if (hasText(schedulerHeadless)) {
            return parseBoolean(
                    SCHEDULER_HEADLESS_ENV,
                    schedulerHeadless,
                    true
            );
        }

        return true;
    }

    private static boolean readBoolean(
            String name,
            boolean fallback
    ) {
        String raw = System.getenv(name);

        if (!hasText(raw)) {
            return fallback;
        }

        return parseBoolean(name, raw, fallback);
    }

    private static boolean parseBoolean(
            String name,
            String raw,
            boolean fallback
    ) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> {
                log.warn(
                        "Invalid {}='{}'. Using default {}.",
                        name,
                        raw,
                        fallback
                );
                yield fallback;
            }
        };
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
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

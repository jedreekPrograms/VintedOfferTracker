package pl.flipbot.playwright.browser;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * Small browser-resource switches that can be enabled or disabled independently
 * when a marketplace compatibility issue needs to be isolated without
 * reverting the rest of the browser-memory work.
 */
@Slf4j
final class BrowserResourceOptimizationConfig {

    static final String BLOCK_SERVICE_WORKERS_ENV =
            "FLIPBOT_BROWSER_BLOCK_SERVICE_WORKERS";

    static final String USE_PLAYWRIGHT_CHROMIUM_ENV =
            "FLIPBOT_BROWSER_USE_PLAYWRIGHT_CHROMIUM";

    private BrowserResourceOptimizationConfig() {
    }

    static boolean blockServiceWorkers(boolean headless) {
        return readHeadlessBoolean(
                headless,
                System.getenv(BLOCK_SERVICE_WORKERS_ENV),
                BLOCK_SERVICE_WORKERS_ENV,
                false
        );
    }

    static boolean blockServiceWorkers(
            boolean headless,
            String rawOverride
    ) {
        return readHeadlessBoolean(
                headless,
                rawOverride,
                BLOCK_SERVICE_WORKERS_ENV,
                false
        );
    }

    static boolean usePlaywrightChromium(boolean headless) {
        return readHeadlessBoolean(
                headless,
                System.getenv(USE_PLAYWRIGHT_CHROMIUM_ENV),
                USE_PLAYWRIGHT_CHROMIUM_ENV,
                false
        );
    }

    static boolean usePlaywrightChromium(
            boolean headless,
            String rawOverride
    ) {
        return readHeadlessBoolean(
                headless,
                rawOverride,
                USE_PLAYWRIGHT_CHROMIUM_ENV,
                false
        );
    }

    private static boolean readHeadlessBoolean(
            boolean headless,
            String rawOverride,
            String environmentName,
            boolean defaultValue
    ) {
        if (!headless) {
            return false;
        }

        if (rawOverride == null || rawOverride.isBlank()) {
            return defaultValue;
        }

        String normalized = rawOverride
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> {
                log.warn(
                        "Invalid {}='{}'. Using default {} for headless browser contexts.",
                        environmentName,
                        rawOverride,
                        defaultValue
                );
                yield defaultValue;
            }
        };
    }
}

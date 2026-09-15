package pl.flipbot.playwright.browser;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * Small browser-resource switches that can be disabled independently when a
 * marketplace compatibility issue needs to be isolated without reverting the
 * rest of the browser-memory work.
 */
@Slf4j
final class BrowserResourceOptimizationConfig {

    static final String BLOCK_SERVICE_WORKERS_ENV =
            "FLIPBOT_BROWSER_BLOCK_SERVICE_WORKERS";

    private BrowserResourceOptimizationConfig() {
    }

    static boolean blockServiceWorkers(boolean headless) {
        return blockServiceWorkers(
                headless,
                System.getenv(BLOCK_SERVICE_WORKERS_ENV)
        );
    }

    static boolean blockServiceWorkers(
            boolean headless,
            String rawOverride
    ) {
        if (!headless) {
            return false;
        }

        if (rawOverride == null || rawOverride.isBlank()) {
            return true;
        }

        String normalized = rawOverride
                .trim()
                .toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> {
                log.warn(
                        "Invalid {}='{}'. Using default true for headless browser contexts.",
                        BLOCK_SERVICE_WORKERS_ENV,
                        rawOverride
                );
                yield true;
            }
        };
    }
}

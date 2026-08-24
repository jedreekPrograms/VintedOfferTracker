package pl.flipbot.playwright.probe;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test-only browser profile for exercising anti-automation checks on a
 * controlled PRICE_PROBE endpoint.
 *
 * This profile is deliberately scoped to the configured PRICE_PROBE base URL.
 * PriceProbeRuntimeConfig already rejects vinted.pl and all of its subdomains,
 * so this class cannot be used to alter the browser fingerprint on Vinted.
 */
@Slf4j
public final class PriceProbeTestStealthProfile {

    public static final String ENABLED_ENV =
            "FLIPBOT_PRICE_PROBE_TEST_STEALTH_ENABLED";
    public static final String MIN_KEY_DELAY_ENV =
            "FLIPBOT_PRICE_PROBE_TEST_KEY_DELAY_MIN_MS";
    public static final String MAX_KEY_DELAY_ENV =
            "FLIPBOT_PRICE_PROBE_TEST_KEY_DELAY_MAX_MS";

    private static final int DEFAULT_MIN_KEY_DELAY_MS = 55;
    private static final int DEFAULT_MAX_KEY_DELAY_MS = 115;
    private static final int MIN_ALLOWED_KEY_DELAY_MS = 10;
    private static final int MAX_ALLOWED_KEY_DELAY_MS = 500;

    private static final String INIT_SCRIPT = """
            (() => {
                const defineGetter = (target, name, getter) => {
                    try {
                        Object.defineProperty(target, name, {
                            get: getter,
                            configurable: true
                        });
                    } catch (_) {
                        // Test page may lock individual properties; keep going.
                    }
                };

                defineGetter(Navigator.prototype, 'webdriver', () => undefined);
                defineGetter(Navigator.prototype, 'languages', () => [
                    'pl-PL', 'pl', 'en-US', 'en'
                ]);

                if (!window.chrome) {
                    try {
                        Object.defineProperty(window, 'chrome', {
                            value: { runtime: {} },
                            configurable: true
                        });
                    } catch (_) {
                        // Non-critical in a test profile.
                    }
                } else if (!window.chrome.runtime) {
                    try {
                        window.chrome.runtime = {};
                    } catch (_) {
                        // Non-critical in a test profile.
                    }
                }

                try {
                    const originalQuery = navigator.permissions?.query?.bind(
                        navigator.permissions
                    );
                    if (originalQuery) {
                        navigator.permissions.query = (parameters) => {
                            if (parameters?.name === 'notifications') {
                                return Promise.resolve({
                                    state: Notification.permission,
                                    onchange: null
                                });
                            }
                            return originalQuery(parameters);
                        };
                    }
                } catch (_) {
                    // Keep native behavior when Permissions API cannot be wrapped.
                }
            })();
            """;

    private final boolean enabled;
    private final int minKeyDelayMs;
    private final int maxKeyDelayMs;

    private PriceProbeTestStealthProfile(
            boolean enabled,
            int minKeyDelayMs,
            int maxKeyDelayMs
    ) {
        this.enabled = enabled;
        this.minKeyDelayMs = minKeyDelayMs;
        this.maxKeyDelayMs = maxKeyDelayMs;
    }

    public static PriceProbeTestStealthProfile fromEnvironment(
            PriceProbeRuntimeConfig config
    ) {
        boolean enabled = readBoolean(ENABLED_ENV, false);
        int minDelay = readBoundedInt(
                MIN_KEY_DELAY_ENV,
                DEFAULT_MIN_KEY_DELAY_MS
        );
        int maxDelay = readBoundedInt(
                MAX_KEY_DELAY_ENV,
                DEFAULT_MAX_KEY_DELAY_MS
        );

        if (maxDelay < minDelay) {
            int swap = minDelay;
            minDelay = maxDelay;
            maxDelay = swap;
        }

        if (enabled) {
            if (config == null
                    || !config.enabled()
                    || !config.isAllowedUrl(config.homeUrl())) {
                throw new IllegalStateException(
                        "PRICE_PROBE test-stealth can run only on the configured non-Vinted test endpoint."
                );
            }

            if (PriceProbeRuntimeConfig.isRealVintedHost(
                    config.baseUri().getHost()
            )) {
                throw new IllegalStateException(
                        "PRICE_PROBE test-stealth is forbidden on vinted.pl and its subdomains."
                );
            }

            log.warn(
                    "[PRICE PROBE TEST] Test-stealth profile ENABLED for controlled endpoint {}. It is scoped to PRICE_PROBE and cannot target Vinted. keyDelay={}..{}ms.",
                    config.baseUri(),
                    minDelay,
                    maxDelay
            );
        }

        return new PriceProbeTestStealthProfile(
                enabled,
                minDelay,
                maxDelay
        );
    }

    public boolean enabled() {
        return enabled;
    }

    public void installBeforeNavigation(Page page) {
        if (!enabled) {
            return;
        }

        page.addInitScript(INIT_SCRIPT);
    }

    public void typeMessage(Locator composer, String message) {
        if (!enabled) {
            composer.fill(message);
            return;
        }

        composer.fill("");

        for (int index = 0; index < message.length(); index++) {
            String character = String.valueOf(message.charAt(index));
            composer.pressSequentially(
                    character,
                    new Locator.PressSequentiallyOptions()
                            .setDelay(nextKeyDelayMs())
            );
        }
    }

    static String initScript() {
        return INIT_SCRIPT;
    }

    int nextKeyDelayMs() {
        if (minKeyDelayMs == maxKeyDelayMs) {
            return minKeyDelayMs;
        }

        return ThreadLocalRandom.current().nextInt(
                minKeyDelayMs,
                maxKeyDelayMs + 1
        );
    }

    private static boolean readBoolean(String name, boolean fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> fallback;
        };
    }

    private static int readBoundedInt(String name, int fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            int value = Integer.parseInt(raw.trim());
            return Math.max(
                    MIN_ALLOWED_KEY_DELAY_MS,
                    Math.min(MAX_ALLOWED_KEY_DELAY_MS, value)
            );
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}

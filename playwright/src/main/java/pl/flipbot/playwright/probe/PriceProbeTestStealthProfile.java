package pl.flipbot.playwright.probe;

import com.microsoft.playwright.BrowserContext;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Locale;

/**
 * Test-only browser fingerprint profile for a controlled PRICE_PROBE endpoint.
 *
 * The init script is origin-scoped. Even though it is registered on the
 * BrowserContext, it changes browser-exposed values only when the document's
 * origin exactly matches FLIPBOT_PRICE_PROBE_BASE_URL. PriceProbeRuntimeConfig
 * rejects vinted.pl and all of its subdomains before this profile can be
 * installed.
 */
@Slf4j
public final class PriceProbeTestStealthProfile {

    public static final String ENABLED_ENV =
            "FLIPBOT_PRICE_PROBE_TEST_STEALTH_ENABLED";

    private static final String DEFAULT_BASE_URL =
            "http://localhost:4173";

    private PriceProbeTestStealthProfile() {}

    public static void installIfEnabled(BrowserContext context) {
        if (!readBoolean(ENABLED_ENV, false)) {
            return;
        }

        if (!readBoolean(PriceProbeRuntimeConfig.ENABLED_ENV, false)) {
            throw new IllegalStateException(
                    ENABLED_ENV + " requires "
                            + PriceProbeRuntimeConfig.ENABLED_ENV
                            + "=true."
            );
        }

        String rawBaseUrl = System.getenv(
                PriceProbeRuntimeConfig.BASE_URL_ENV
        );

        URI baseUri = PriceProbeRuntimeConfig.validateBaseUrl(
                rawBaseUrl == null || rawBaseUrl.isBlank()
                        ? DEFAULT_BASE_URL
                        : rawBaseUrl
        );

        if (PriceProbeRuntimeConfig.isRealVintedHost(baseUri.getHost())) {
            throw new IllegalStateException(
                    "PRICE_PROBE test-stealth cannot target vinted.pl or its subdomains."
            );
        }

        context.addInitScript(scopedInitScript(baseUri));

        log.warn(
                "[PRICE PROBE TEST] Test-only browser fingerprint profile enabled for exact origin {}. Other origins, including Vinted, keep the normal browser profile.",
                baseUri
        );
    }

    static String scopedInitScript(URI baseUri) {
        URI validated = PriceProbeRuntimeConfig.validateBaseUrl(
                baseUri.toString()
        );
        String allowedOrigin = validated.toString();
        String quotedOrigin = quoteForJavaScript(allowedOrigin);

        return """
                (() => {
                    const allowedOrigin = %s;
                    if (location.origin !== allowedOrigin) {
                        return;
                    }

                    const defineGetter = (target, name, getter) => {
                        try {
                            Object.defineProperty(target, name, {
                                get: getter,
                                configurable: true
                            });
                        } catch (_) {
                            // The controlled test page may lock a property.
                        }
                    };

                    defineGetter(
                        Navigator.prototype,
                        'webdriver',
                        () => undefined
                    );

                    defineGetter(
                        Navigator.prototype,
                        'languages',
                        () => ['pl-PL', 'pl', 'en-US', 'en']
                    );

                    try {
                        if (!window.chrome) {
                            Object.defineProperty(window, 'chrome', {
                                value: { runtime: {} },
                                configurable: true
                            });
                        } else if (!window.chrome.runtime) {
                            window.chrome.runtime = {};
                        }
                    } catch (_) {
                        // Non-critical for the controlled test profile.
                    }

                    try {
                        const originalQuery =
                            navigator.permissions?.query?.bind(
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
                        // Fall back to the native Permissions API.
                    }
                })();
                """.formatted(quotedOrigin);
    }

    private static String quoteForJavaScript(String value) {
        return "\""
                + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                + "\"";
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
}

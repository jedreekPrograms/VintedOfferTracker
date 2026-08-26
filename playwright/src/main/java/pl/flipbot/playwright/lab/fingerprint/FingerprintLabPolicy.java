package pl.flipbot.playwright.lab.fingerprint;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Safety boundary for the fingerprint laboratory.
 *
 * The lab is intentionally unavailable on production websites. Even when the
 * feature flag is enabled, experiments are restricted to loopback hosts and
 * reserved .test names so the code can be used to study fingerprint surfaces
 * without affecting marketplace traffic.
 */
public final class FingerprintLabPolicy {

    public static final String ENABLE_ENV = "FLIPBOT_FINGERPRINT_LAB";

    private static final Set<String> LOOPBACK_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "::1"
    );

    private FingerprintLabPolicy() {}

    public static boolean isEnabled() {
        return Boolean.parseBoolean(
                System.getenv().getOrDefault(ENABLE_ENV, "false")
        );
    }

    public static boolean isAllowedUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = normalize(uri.getScheme());
            String host = normalize(uri.getHost());

            if (!(scheme.equals("http") || scheme.equals("https"))) {
                return false;
            }

            if (host.isBlank()) {
                return false;
            }

            return LOOPBACK_HOSTS.contains(host)
                    || host.endsWith(".localhost")
                    || host.equals("test")
                    || host.endsWith(".test");

        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public static void requireAllowed(String rawUrl) {
        if (!isEnabled()) {
            throw new IllegalStateException(
                    "Fingerprint lab is disabled. Set "
                            + ENABLE_ENV
                            + "=true only for local laboratory runs."
            );
        }

        if (!isAllowedUrl(rawUrl)) {
            throw new IllegalStateException(
                    "Fingerprint lab refuses non-laboratory URL: "
                            + rawUrl
                            + ". Allowed hosts are loopback, *.localhost and *.test only."
            );
        }
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}

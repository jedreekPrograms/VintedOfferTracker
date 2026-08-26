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
        return isAllowedNetworkUrl(rawUrl, Set.of("http", "https"));
    }

    public static boolean isAllowedWebSocketUrl(String rawUrl) {
        return isAllowedNetworkUrl(rawUrl, Set.of("ws", "wss"));
    }

    /**
     * Optional proxy endpoints are laboratory-only as well. This allows a
     * controlled local proxy harness without providing a path to production
     * residential/mobile proxy infrastructure.
     */
    public static boolean isAllowedProxyUrl(String rawUrl) {
        return isAllowedNetworkUrl(
                rawUrl,
                Set.of("http", "https", "socks5")
        );
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

    public static void requireAllowedProxy(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }

        if (!isEnabled()) {
            throw new IllegalStateException(
                    "Fingerprint lab proxy configuration requires "
                            + ENABLE_ENV
                            + "=true."
            );
        }

        if (!isAllowedProxyUrl(rawUrl)) {
            throw new IllegalStateException(
                    "Fingerprint lab refuses non-laboratory proxy URL: "
                            + rawUrl
                            + ". Proxy hosts are restricted to loopback, *.localhost and *.test."
            );
        }
    }

    private static boolean isAllowedNetworkUrl(
            String rawUrl,
            Set<String> allowedSchemes
    ) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = normalize(uri.getScheme());
            String host = normalize(uri.getHost());

            if (!allowedSchemes.contains(scheme) || host.isBlank()) {
                return false;
            }

            return isLaboratoryHost(host);

        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean isLaboratoryHost(String host) {
        return LOOPBACK_HOSTS.contains(host)
                || host.endsWith(".localhost")
                || host.equals("test")
                || host.endsWith(".test");
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}

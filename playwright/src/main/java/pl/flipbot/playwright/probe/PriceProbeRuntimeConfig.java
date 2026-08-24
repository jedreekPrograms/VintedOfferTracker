package pl.flipbot.playwright.probe;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.util.Locale;

@Slf4j
public record PriceProbeRuntimeConfig(
        boolean enabled,
        URI baseUri,
        int maxPerJob
) {

    static final String ENABLED_ENV =
            "FLIPBOT_PRICE_PROBE_ENABLED";
    static final String BASE_URL_ENV =
            "FLIPBOT_PRICE_PROBE_BASE_URL";
    static final String MAX_PER_JOB_ENV =
            "FLIPBOT_PRICE_PROBE_MAX_PER_JOB";

    public static PriceProbeRuntimeConfig fromEnvironment() {
        boolean enabled = readBoolean(ENABLED_ENV, false);
        int maxPerJob = readInt(MAX_PER_JOB_ENV, 1, 1, 5);
        String rawBaseUrl = System.getenv(BASE_URL_ENV);

        URI baseUri = null;

        if (enabled) {
            baseUri = validateSandboxBaseUrl(rawBaseUrl);
        }

        PriceProbeRuntimeConfig config =
                new PriceProbeRuntimeConfig(
                        enabled,
                        baseUri,
                        maxPerJob
                );

        if (enabled) {
            log.warn(
                    "[PRICE PROBE CONFIG] SANDBOX price probes enabled. baseUrl={}, maxPerJob={}. Real vinted.pl hosts are hard-blocked by this module.",
                    baseUri,
                    maxPerJob
            );
        } else {
            log.info(
                    "[PRICE PROBE CONFIG] Sandbox price probes are disabled."
            );
        }

        return config;
    }

    public boolean isAllowedUrl(String rawUrl) {
        if (!enabled || baseUri == null || rawUrl == null || rawUrl.isBlank()) {
            return false;
        }

        try {
            URI candidate = URI.create(rawUrl.trim());

            if (isRealVintedHost(candidate.getHost())) {
                return false;
            }

            return sameEndpoint(baseUri, candidate)
                    && isAllowedScheme(candidate);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public String homeUrl() {
        if (!enabled || baseUri == null) {
            throw new IllegalStateException(
                    "Sandbox price probes are not enabled."
            );
        }

        String raw = baseUri.toString();
        return raw.endsWith("/") ? raw : raw + "/";
    }

    static URI validateSandboxBaseUrl(String rawBaseUrl) {
        if (rawBaseUrl == null || rawBaseUrl.isBlank()) {
            throw new IllegalStateException(
                    BASE_URL_ENV
                            + " is required when sandbox price probes are enabled."
            );
        }

        URI uri;
        try {
            uri = URI.create(rawBaseUrl.trim());
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    BASE_URL_ENV + " must be a valid absolute URL.",
                    exception
            );
        }

        if (!uri.isAbsolute()
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalStateException(
                    BASE_URL_ENV
                            + " must contain only scheme, host and optional port."
            );
        }

        String path = uri.getPath();
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            throw new IllegalStateException(
                    BASE_URL_ENV + " cannot contain a path."
            );
        }

        if (isRealVintedHost(uri.getHost())) {
            throw new IllegalStateException(
                    "Sandbox price probes cannot target vinted.pl or any of its subdomains."
            );
        }

        if (!isAllowedScheme(uri)) {
            throw new IllegalStateException(
                    "Sandbox price probe base URL must use HTTPS; HTTP is allowed only for localhost/127.0.0.1."
            );
        }

        return normalizeBaseUri(uri);
    }

    static boolean isRealVintedHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return false;
        }

        String host = rawHost.trim().toLowerCase(Locale.ROOT);
        return "vinted.pl".equals(host)
                || host.endsWith(".vinted.pl");
    }

    private static boolean sameEndpoint(URI expected, URI candidate) {
        return normalize(expected.getScheme()).equals(normalize(candidate.getScheme()))
                && normalize(expected.getHost()).equals(normalize(candidate.getHost()))
                && effectivePort(expected) == effectivePort(candidate);
    }

    private static boolean isAllowedScheme(URI uri) {
        String scheme = normalize(uri.getScheme());
        String host = normalize(uri.getHost());

        if ("https".equals(scheme)) {
            return true;
        }

        return "http".equals(scheme)
                && ("localhost".equals(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host));
    }

    private static URI normalizeBaseUri(URI uri) {
        String scheme = normalize(uri.getScheme());
        String host = normalize(uri.getHost());
        int port = uri.getPort();

        String value = scheme + "://" + host;
        if (port >= 0) {
            value += ":" + port;
        }

        return URI.create(value);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }

        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean readBoolean(
            String name,
            boolean fallback
    ) {
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

    private static int readInt(
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
}

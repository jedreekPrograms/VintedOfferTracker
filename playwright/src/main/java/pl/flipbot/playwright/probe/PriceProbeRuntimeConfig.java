package pl.flipbot.playwright.probe;

import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

@Slf4j
public record PriceProbeRuntimeConfig(
        boolean enabled,
        URI baseUri
) {

    public static final String ENABLED_ENV = "FLIPBOT_PRICE_PROBE_ENABLED";
    public static final String BASE_URL_ENV = "FLIPBOT_PRICE_PROBE_BASE_URL";
    private static final String DEFAULT_BASE_URL = "http://localhost:4173";

    public static PriceProbeRuntimeConfig fromEnvironment() {
        boolean requested = readBoolean(ENABLED_ENV, false);

        if (!requested) {
            log.info(
                    "[PRICE PROBE CONFIG] PRICE_PROBE is disabled."
            );
            return disabledDefault();
        }

        String rawBaseUrl = System.getenv(BASE_URL_ENV);
        String configuredBaseUrl =
                rawBaseUrl == null || rawBaseUrl.isBlank()
                        ? DEFAULT_BASE_URL
                        : rawBaseUrl;

        try {
            URI baseUri = validateBaseUrl(configuredBaseUrl);

            log.warn(
                    "[PRICE PROBE CONFIG] PRICE_PROBE is ENABLED. baseUrl={}. Set {}=false to stop all new probe jobs.",
                    baseUri,
                    ENABLED_ENV
            );

            return new PriceProbeRuntimeConfig(true, baseUri);

        } catch (RuntimeException exception) {
            /*
             * PRICE_PROBE is an optional test-only subsystem. A forbidden or
             * malformed target must remain fail-closed, but it must not prevent
             * FlipBotPlaywrightApplication from starting normal workers and
             * market statistics. Keep the probe disabled and continue with the
             * known-safe loopback endpoint as inert configuration state.
             */
            log.warn(
                    "[PRICE PROBE CONFIG] BLOCKED/SKIPPED. Normal FlipBot runtime will continue. requestedBaseUrl={}, reason={}",
                    configuredBaseUrl,
                    safeMessage(exception)
            );

            return disabledDefault();
        }
    }

    public boolean isAllowedUrl(String rawUrl) {
        if (!enabled || rawUrl == null || rawUrl.isBlank()) {
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

    public String mappedListingUrl(String sourceListingUrl) {
        if (!enabled) {
            throw new IllegalStateException("Price probes are disabled.");
        }

        if (sourceListingUrl == null || sourceListingUrl.isBlank()) {
            throw new IllegalArgumentException("Source listing URL cannot be blank.");
        }

        URI source = URI.create(sourceListingUrl.trim());
        String path = source.getRawPath();

        if (path == null || path.isBlank() || !path.startsWith("/")) {
            throw new IllegalArgumentException("Source listing URL must contain an absolute path.");
        }

        try {
            URI mapped = new URI(
                    baseUri.getScheme(),
                    null,
                    baseUri.getHost(),
                    baseUri.getPort(),
                    path,
                    source.getRawQuery(),
                    null
            );

            if (!isAllowedUrl(mapped.toString())) {
                throw new IllegalStateException("Mapped listing URL is outside the configured probe endpoint.");
            }

            return mapped.toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Could not map source listing URL.", exception);
        }
    }

    public String homeUrl() {
        String raw = baseUri.toString();
        return raw.endsWith("/") ? raw : raw + "/";
    }

    static URI validateBaseUrl(String rawBaseUrl) {
        URI uri = URI.create(rawBaseUrl.trim());

        if (!uri.isAbsolute()
                || uri.getHost() == null
                || uri.getHost().isBlank()
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalStateException(
                    BASE_URL_ENV + " must contain only scheme, host and optional port."
            );
        }

        String path = uri.getPath();
        if (path != null && !path.isBlank() && !"/".equals(path)) {
            throw new IllegalStateException(BASE_URL_ENV + " cannot contain a path.");
        }

        if (isRealVintedHost(uri.getHost())) {
            throw new IllegalStateException(
                    "PRICE_PROBE test runtime cannot target vinted.pl or its subdomains."
            );
        }

        if (!isAllowedScheme(uri)) {
            throw new IllegalStateException(
                    "PRICE_PROBE base URL must use HTTPS; HTTP is allowed only for localhost/127.0.0.1."
            );
        }

        return normalizeBaseUri(uri);
    }

    static boolean isRealVintedHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            return false;
        }

        String host = canonicalDnsHost(rawHost);
        return "vinted.pl".equals(host) || host.endsWith(".vinted.pl");
    }

    private static String canonicalDnsHost(String rawHost) {
        String host = rawHost.trim().toLowerCase(Locale.ROOT);

        /*
         * A trailing DNS root dot does not identify a different Internet host:
         * www.vinted.pl. and www.vinted.pl resolve to the same FQDN. The probe
         * blacklist must therefore compare their canonical DNS form instead of
         * allowing a final dot to bypass the explicit Vinted prohibition.
         */
        while (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }

        return host;
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
        String value = normalize(uri.getScheme()) + "://" + normalize(uri.getHost());
        if (uri.getPort() >= 0) {
            value += ":" + uri.getPort();
        }
        return URI.create(value);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static PriceProbeRuntimeConfig disabledDefault() {
        return new PriceProbeRuntimeConfig(
                false,
                validateBaseUrl(DEFAULT_BASE_URL)
        );
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return throwable == null
                    ? "unknown error"
                    : throwable.getClass().getSimpleName();
        }

        return throwable.getMessage()
                .lines()
                .findFirst()
                .orElse(throwable.getMessage())
                .trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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

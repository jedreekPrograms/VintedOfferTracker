package pl.flipbot.playwright.api;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

final class BackendApiEndpoint {

    private static final String ENV_NAME = "FLIPBOT_BACKEND_URL";
    private static final String DEFAULT_URL = "http://127.0.0.1:8081";
    private static final Set<String> LOOPBACK_HOSTS = Set.of(
            "localhost",
            "127.0.0.1",
            "::1",
            "0:0:0:0:0:0:0:1"
    );

    private BackendApiEndpoint() {
    }

    static URI fromEnvironment() {
        String configured = System.getenv(ENV_NAME);
        return resolve(configured == null || configured.isBlank()
                ? DEFAULT_URL
                : configured);
    }

    static URI resolve(String configured) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("Backend URL cannot be blank");
        }

        final URI parsed;
        try {
            parsed = URI.create(configured.trim());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "FLIPBOT_BACKEND_URL is not a valid URI",
                    exception
            );
        }

        String scheme = parsed.getScheme();
        String host = parsed.getHost();

        if (scheme == null || host == null) {
            throw new IllegalArgumentException(
                    "FLIPBOT_BACKEND_URL must contain an explicit http(s) scheme and host"
            );
        }

        scheme = scheme.toLowerCase(Locale.ROOT);
        host = host.toLowerCase(Locale.ROOT);

        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException(
                    "FLIPBOT_BACKEND_URL must use http or https"
            );
        }

        if (parsed.getUserInfo() != null
                || parsed.getQuery() != null
                || parsed.getFragment() != null) {
            throw new IllegalArgumentException(
                    "FLIPBOT_BACKEND_URL must not contain credentials, query parameters or a fragment"
            );
        }

        String path = parsed.getPath();
        if (path != null && !path.isBlank() && !path.equals("/")) {
            throw new IllegalArgumentException(
                    "FLIPBOT_BACKEND_URL must point at the backend origin, without an extra path"
            );
        }

        if (scheme.equals("http") && !LOOPBACK_HOSTS.contains(host)) {
            throw new IllegalArgumentException(
                    "Refusing plaintext HTTP to non-loopback backend host '"
                            + host
                            + "'. Use HTTPS for remote backend communication."
            );
        }

        try {
            return new URI(
                    scheme,
                    null,
                    parsed.getHost(),
                    parsed.getPort(),
                    null,
                    null,
                    null
            );
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Could not normalize FLIPBOT_BACKEND_URL",
                    exception
            );
        }
    }
}

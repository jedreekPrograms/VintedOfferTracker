package pl.flipbot.playwright.marketplace;

import pl.flipbot.playwright.lab.fingerprint.ControlledTestRuntime;
import pl.flipbot.playwright.lab.fingerprint.FingerprintLabPolicy;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

public final class MarketplaceUrls {

    private static final URI PRODUCTION_ORIGIN =
            URI.create("https://www.vinted.pl");

    /*
     * Environment variables are process-level configuration, so resolving the
     * marketplace origin once at class initialization keeps every consumer in
     * one JVM on the same target. Invalid/forbidden controlled-test settings
     * fail closed back to the normal production marketplace runtime; the
     * controlled test modules themselves remain BLOCKED/SKIPPED by their own
     * policy.
     */
    private static final URI RUNTIME_ORIGIN = resolveRuntimeOrigin();
    private static final boolean CONTROLLED_TEST_ORIGIN =
            !sameEndpoint(PRODUCTION_ORIGIN, RUNTIME_ORIGIN);

    private MarketplaceUrls() {
    }

    public static final String HOME =
            originUrl(RUNTIME_ORIGIN) + "/";

    public static final String CATALOG =
            originUrl(RUNTIME_ORIGIN) + "/catalog";

    public static final String INBOX =
            originUrl(RUNTIME_ORIGIN) + "/inbox";

    public static boolean isControlledTestRuntime() {
        return CONTROLLED_TEST_ORIGIN;
    }

    public static String runtimeOrigin() {
        return originUrl(RUNTIME_ORIGIN);
    }

    /**
     * Production mode trusts only normal HTTPS URLs on the real Polish Vinted
     * host (or its subdomains). In controlled-test mode the same existing
     * marketplace guards are reused, but they trust only the exact loopback or
     * reserved .test origin selected before process startup.
     *
     * Prefix checks are intentionally avoided. User-info is always rejected.
     * Explicit ports remain forbidden for production Vinted, while a test
     * origin may use exactly the port configured in FLIPBOT_TEST_URL.
     */
    public static boolean isVintedUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = normalize(uri.getScheme());
            String host = normalize(uri.getHost());

            if (uri.getRawUserInfo() != null || host.isBlank()) {
                return false;
            }

            if (CONTROLLED_TEST_ORIGIN) {
                return isExactControlledOrigin(RUNTIME_ORIGIN, rawUrl);
            }

            if (!"https".equals(scheme) || uri.getPort() != -1) {
                return false;
            }

            return "vinted.pl".equals(host)
                    || host.endsWith(".vinted.pl");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static boolean isExactControlledOrigin(
            URI expectedOrigin,
            String rawUrl
    ) {
        if (expectedOrigin == null || rawUrl == null || rawUrl.isBlank()) {
            return false;
        }

        try {
            URI candidate = URI.create(rawUrl.trim());
            return candidate.getRawUserInfo() == null
                    && FingerprintLabPolicy.isAllowedUrl(rawUrl)
                    && sameEndpoint(expectedOrigin, candidate);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static boolean isCatalogUrl(String rawUrl) {
        if (!isVintedUrl(rawUrl)) {
            return false;
        }

        try {
            String path = URI.create(rawUrl.trim()).getPath();
            return path != null
                    && ("/catalog".equals(path)
                    || path.startsWith("/catalog/"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * Resolve a listing URL supplied by the scanner/backend and prove that it
     * still points to the expected item on the currently trusted marketplace
     * origin.
     */
    public static String resolveVintedListingUrl(
            String rawUrl,
            String expectedListingId
    ) {
        if (expectedListingId == null
                || expectedListingId.isBlank()
                || !expectedListingId.trim().matches("^\\d+$")) {
            throw new IllegalArgumentException(
                    "Marketplace listing id must contain digits only"
            );
        }

        String resolved = resolveTrustedVintedUrl(rawUrl, "listing");

        if (!isVintedListingUrl(resolved, expectedListingId)) {
            throw new IllegalArgumentException(
                    "Refusing marketplace listing URL that is not the expected trusted marketplace item. "
                            + "listingId="
                            + expectedListingId
                            + ", url="
                            + rawUrl
            );
        }

        return resolved;
    }

    public static boolean isVintedListingUrl(
            String rawUrl,
            String expectedListingId
    ) {
        if (!isVintedUrl(rawUrl)
                || expectedListingId == null
                || expectedListingId.isBlank()
                || !expectedListingId.trim().matches("^\\d+$")) {
            return false;
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String path = uri.getPath();
            String expectedPrefix = "/items/" + expectedListingId.trim();

            return path != null
                    && (path.equals(expectedPrefix)
                    || path.startsWith(expectedPrefix + "-"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public static String resolveVintedConversationUrl(
            String rawUrl,
            String expectedConversationId
    ) {
        if (!isSafePathSegment(expectedConversationId)) {
            throw new IllegalArgumentException(
                    "Conversation id must be a non-blank URL path segment"
            );
        }

        String resolved = resolveTrustedVintedUrl(rawUrl, "conversation");

        if (!isVintedConversationUrl(resolved, expectedConversationId)) {
            throw new IllegalArgumentException(
                    "Refusing conversation URL that is not the expected trusted marketplace inbox conversation. "
                            + "conversationId="
                            + expectedConversationId
                            + ", url="
                            + rawUrl
            );
        }

        return resolved;
    }

    public static boolean isVintedConversationUrl(
            String rawUrl,
            String expectedConversationId
    ) {
        if (!isVintedUrl(rawUrl)
                || !isSafePathSegment(expectedConversationId)) {
            return false;
        }

        try {
            String path = URI.create(rawUrl.trim()).getPath();
            return ("/inbox/" + expectedConversationId).equals(path);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static String resolveTrustedVintedUrl(
            String rawUrl,
            String label
    ) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException(
                    capitalize(label) + " URL cannot be empty"
            );
        }

        String trimmedUrl = rawUrl.trim();
        if (trimmedUrl.startsWith("//")) {
            throw new IllegalArgumentException(
                    "Protocol-relative " + label + " URLs are not trusted: "
                            + rawUrl
            );
        }

        final String resolved;
        try {
            URI candidate = URI.create(trimmedUrl);
            resolved = candidate.isAbsolute()
                    ? candidate.toString()
                    : URI.create(HOME).resolve(candidate).toString();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid marketplace " + label + " URL: " + rawUrl,
                    exception
            );
        }

        if (!isVintedUrl(resolved)) {
            throw new IllegalArgumentException(
                    "Refusing URL outside the currently trusted marketplace origin for "
                            + label
                            + ": "
                            + rawUrl
            );
        }

        return resolved;
    }

    private static URI resolveRuntimeOrigin() {
        if (!ControlledTestRuntime.isEnabled()) {
            return PRODUCTION_ORIGIN;
        }

        try {
            ControlledTestRuntime.requireValidConfiguration();
            URI configured = URI.create(
                    ControlledTestRuntime.targetUrl().trim()
            );

            if (configured.getRawUserInfo() != null) {
                return PRODUCTION_ORIGIN;
            }

            return new URI(
                    normalize(configured.getScheme()),
                    null,
                    normalize(configured.getHost()),
                    configured.getPort(),
                    null,
                    null,
                    null
            );
        } catch (RuntimeException | URISyntaxException exception) {
            return PRODUCTION_ORIGIN;
        }
    }

    private static boolean sameEndpoint(URI expected, URI candidate) {
        return normalize(expected.getScheme()).equals(normalize(candidate.getScheme()))
                && normalize(expected.getHost()).equals(normalize(candidate.getHost()))
                && effectivePort(expected) == effectivePort(candidate);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static String originUrl(URI uri) {
        try {
            return new URI(
                    normalize(uri.getScheme()),
                    null,
                    normalize(uri.getHost()),
                    uri.getPort(),
                    null,
                    null,
                    null
            ).toString();
        } catch (URISyntaxException exception) {
            throw new IllegalStateException(
                    "Could not build trusted marketplace origin",
                    exception
            );
        }
    }

    private static boolean isSafePathSegment(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        String trimmed = value.trim();
        return !trimmed.contains("/")
                && !trimmed.contains("\\")
                && !trimmed.equals(".")
                && !trimmed.equals("..");
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Marketplace";
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}

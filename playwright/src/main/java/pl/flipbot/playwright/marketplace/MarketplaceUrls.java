package pl.flipbot.playwright.marketplace;

import java.net.URI;
import java.util.Locale;

public final class MarketplaceUrls {

    private MarketplaceUrls() {
    }

    public static final String HOME =
            "https://www.vinted.pl/";

    public static final String CATALOG =
            "https://www.vinted.pl/catalog";

    public static final String INBOX =
            "https://www.vinted.pl/inbox";

    /**
     * Treat only normal HTTPS URLs on the real Polish Vinted host (or one of
     * its subdomains) as trusted. Prefix checks are intentionally avoided
     * because a lookalike host such as www.vinted.pl.example.com must never
     * pass. User-info and explicit ports are not part of normal marketplace
     * navigation and are rejected at this trust boundary as well.
     */
    public static boolean isVintedUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = normalize(uri.getScheme());
            String host = normalize(uri.getHost());

            if (!"https".equals(scheme)
                    || uri.getRawUserInfo() != null
                    || uri.getPort() != -1) {
                return false;
            }

            return "vinted.pl".equals(host)
                    || host.endsWith(".vinted.pl");
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
     * still points to the expected item on trusted Polish Vinted.
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
                    "Refusing marketplace listing URL that is not the expected trusted Vinted item. "
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
                    "Refusing conversation URL that is not the expected trusted Vinted inbox conversation. "
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
                    "Refusing non-Vinted " + label + " URL: " + rawUrl
            );
        }

        return resolved;
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

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
     * Treat only HTTPS URLs on the real Polish Vinted host (or one of its
     * subdomains) as trusted. Prefix checks are intentionally avoided because
     * a lookalike host such as www.vinted.pl.example.com must never pass.
     */
    public static boolean isVintedUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = normalize(uri.getScheme());
            String host = normalize(uri.getHost());

            if (!"https".equals(scheme)) {
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
     *
     * Relative item links are normal Vinted output and are resolved against
     * {@link #HOME}. Absolute URLs are accepted only when they already satisfy
     * the same HTTPS/host/item-id contract. Protocol-relative URLs are rejected
     * rather than inheriting a host implicitly.
     */
    public static String resolveVintedListingUrl(
            String rawUrl,
            String expectedListingId
    ) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("Listing URL cannot be empty");
        }

        if (expectedListingId == null
                || expectedListingId.isBlank()
                || !expectedListingId.trim().matches("^\\d+$")) {
            throw new IllegalArgumentException(
                    "Marketplace listing id must contain digits only"
            );
        }

        String trimmedUrl = rawUrl.trim();
        if (trimmedUrl.startsWith("//")) {
            throw new IllegalArgumentException(
                    "Protocol-relative listing URLs are not trusted: "
                            + rawUrl
            );
        }

        final String resolved;
        try {
            URI candidate = URI.create(trimmedUrl);

            if (candidate.isAbsolute()) {
                resolved = candidate.toString();
            } else {
                resolved = URI.create(HOME)
                        .resolve(candidate)
                        .toString();
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Invalid marketplace listing URL: " + rawUrl,
                    exception
            );
        }

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

    private static String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }
}

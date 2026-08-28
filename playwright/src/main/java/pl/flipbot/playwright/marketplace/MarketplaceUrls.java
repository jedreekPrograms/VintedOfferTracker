package pl.flipbot.playwright.marketplace;

import java.net.URI;
import java.util.Locale;

public final class MarketplaceUrls {

    private static final String SESSION_REFRESH_PATH =
            "/session-refresh";

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

    public static boolean isSessionRefreshUrl(String rawUrl) {
        if (!isVintedUrl(rawUrl)) {
            return false;
        }

        try {
            String path = URI.create(rawUrl.trim()).getPath();
            return path != null
                    && (SESSION_REFRESH_PATH.equals(path)
                    || (SESSION_REFRESH_PATH + "/").equals(path));
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

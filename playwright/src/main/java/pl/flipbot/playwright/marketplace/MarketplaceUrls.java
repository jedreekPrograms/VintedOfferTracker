package pl.flipbot.playwright.marketplace;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

public final class MarketplaceUrls {

    private static final String SESSION_REFRESH_PATH =
            "/session-refresh";

    private static final Pattern CANONICAL_CATEGORY_PATH =
            Pattern.compile("^/catalog/\\d+(?:-[^/]+)?/?$");

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
     * Vinted exposes the selected category in two equivalent URL shapes.
     * Authenticated catalog variants commonly use {@code catalog[]}, while
     * the anonymous catalog can navigate to a canonical path such as
     * {@code /catalog/3748-vcrs}. Both forms prove that a category is active.
     */
    public static boolean hasCatalogSelection(String rawUrl) {
        if (!isCatalogUrl(rawUrl)) {
            return false;
        }

        try {
            URI uri = URI.create(rawUrl.trim());

            if (CANONICAL_CATEGORY_PATH.matcher(uri.getPath()).matches()) {
                return true;
            }

            String query = uri.getRawQuery();

            if (query == null || query.isBlank()) {
                return false;
            }

            for (String parameter : query.split("&")) {
                int equalsIndex = parameter.indexOf('=');
                String rawName = equalsIndex >= 0
                        ? parameter.substring(0, equalsIndex)
                        : parameter;
                String rawValue = equalsIndex >= 0
                        ? parameter.substring(equalsIndex + 1)
                        : "";

                if ("catalog[]".equals(URLDecoder.decode(
                        rawName,
                        StandardCharsets.UTF_8
                )) && !URLDecoder.decode(
                        rawValue,
                        StandardCharsets.UTF_8
                ).isBlank()) {
                    return true;
                }
            }

            return false;
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

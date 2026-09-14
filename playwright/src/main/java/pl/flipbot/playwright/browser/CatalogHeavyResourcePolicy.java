package pl.flipbot.playwright.browser;

import pl.flipbot.playwright.marketplace.MarketplaceUrls;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Decides whether a heavy subresource can be skipped while a headless worker is
 * on the Vinted catalog.
 *
 * <p>FlipBot reads catalog image URLs from the DOM {@code src} attribute; it
 * does not inspect decoded pixels. Aborting the image/media transfer therefore
 * keeps the listing metadata available while avoiding image decode/GPU/cache
 * pressure. Scripts, stylesheets, documents and API/XHR traffic are never
 * blocked here.</p>
 */
final class CatalogHeavyResourcePolicy {

    private static final Set<String> HEAVY_RESOURCE_TYPES =
            Set.of("image", "media");

    private static final Set<String> CHALLENGE_HOSTS = Set.of(
            "challenges.cloudflare.com",
            "hcaptcha.com",
            "recaptcha.net"
    );

    private CatalogHeavyResourcePolicy() {
    }

    static boolean shouldBlock(
            String topLevelPageUrl,
            String resourceType,
            String requestUrl
    ) {
        if (!MarketplaceUrls.isCatalogUrl(topLevelPageUrl)) {
            return false;
        }

        String normalizedType = normalize(resourceType);
        if (!HEAVY_RESOURCE_TYPES.contains(normalizedType)) {
            return false;
        }

        return !isChallengeResource(requestUrl);
    }

    static boolean isChallengeResource(String requestUrl) {
        if (requestUrl == null || requestUrl.isBlank()) {
            return false;
        }

        try {
            URI uri = URI.create(requestUrl.trim());
            String host = normalize(uri.getHost());
            String path = uri.getPath() == null
                    ? ""
                    : uri.getPath().toLowerCase(Locale.ROOT);

            if (host.isBlank()) {
                return false;
            }

            for (String challengeHost : CHALLENGE_HOSTS) {
                if (host.equals(challengeHost)
                        || host.endsWith("." + challengeHost)) {
                    return true;
                }
            }

            if ((host.equals("google.com")
                    || host.endsWith(".google.com")
                    || host.equals("gstatic.com")
                    || host.endsWith(".gstatic.com"))
                    && path.contains("/recaptcha/")) {
                return true;
            }

            return false;
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

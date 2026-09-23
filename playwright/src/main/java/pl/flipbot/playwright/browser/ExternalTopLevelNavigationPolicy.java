package pl.flipbot.playwright.browser;

import pl.flipbot.playwright.marketplace.MarketplaceUrls;

/**
 * Prevents FlipBot's top-level browser pages from leaving Vinted for unrelated
 * third-party sites.
 *
 * <p>Subresources and iframes are not covered here; the policy applies only to
 * top-level document navigations selected by BrowserManager. Known CAPTCHA /
 * challenge destinations remain allowed so manual recovery cannot be broken.</p>
 */
final class ExternalTopLevelNavigationPolicy {

    private ExternalTopLevelNavigationPolicy() {
    }

    static boolean shouldBlock(String requestUrl) {
        if (requestUrl == null || requestUrl.isBlank()) {
            return false;
        }

        String normalized = requestUrl.trim();

        if (normalized.startsWith("about:")
                || normalized.startsWith("data:")
                || normalized.startsWith("blob:")) {
            return false;
        }

        if (MarketplaceUrls.isVintedUrl(normalized)) {
            return false;
        }

        if (CatalogHeavyResourcePolicy.isChallengeResource(normalized)) {
            return false;
        }

        return normalized.startsWith("http://")
                || normalized.startsWith("https://");
    }
}

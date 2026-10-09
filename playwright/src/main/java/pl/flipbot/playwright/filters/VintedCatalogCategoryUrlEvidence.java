package pl.flipbot.playwright.filters;

import pl.flipbot.playwright.marketplace.MarketplaceUrls;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Strict persistence evidence for a verified native Vinted catalog category.
 *
 * The live picker/URL probe established that selecting a leaf category writes
 * one catalog[] query parameter containing the exact clicked native row ID.
 * Reject conflicting or duplicated catalog[] values even if the first one
 * happens to match the expected category. This helper is deliberately used
 * only when exact DOM provenance was available; legacy pickers keep their
 * older compatibility path in FilterActions.
 */
final class VintedCatalogCategoryUrlEvidence {

    private static final String CATEGORY_PARAMETER = "catalog[]";

    private VintedCatalogCategoryUrlEvidence() {
    }

    static boolean matchesExactlyOneCategory(String rawUrl, String expectedCategoryId) {
        if (expectedCategoryId == null || !expectedCategoryId.matches("[0-9]+")
                || !MarketplaceUrls.isCatalogUrl(rawUrl)) {
            return false;
        }

        try {
            String query = URI.create(rawUrl.trim()).getRawQuery();
            if (query == null || query.isEmpty()) {
                return false;
            }

            String foundId = null;
            for (String pair : query.split("&", -1)) {
                int separator = pair.indexOf('=');
                String name = separator < 0 ? pair : pair.substring(0, separator);
                String value = separator < 0 ? "" : pair.substring(separator + 1);

                String decodedName = URLDecoder.decode(name, StandardCharsets.UTF_8);
                if (!CATEGORY_PARAMETER.equals(decodedName)) {
                    continue;
                }

                // The observed format contains precisely one catalog[].
                // Multiple entries, including two copies of the expected ID,
                // are ambiguous and must not count as persistence proof.
                if (foundId != null) {
                    return false;
                }

                foundId = URLDecoder.decode(value, StandardCharsets.UTF_8);
            }

            return expectedCategoryId.equals(foundId);
        } catch (RuntimeException ignored) {
            // Malformed URLs or percent escapes cannot prove a category.
            return false;
        }
    }
}

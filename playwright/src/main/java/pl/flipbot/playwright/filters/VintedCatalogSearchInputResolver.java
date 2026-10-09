package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;

/**
 * Finds the visible catalog text-search input.
 *
 * Confirmed from a real Vinted catalog DOM snapshot: search-text--input is
 * present twice (desktop header and responsive search bar). Never use an
 * unscoped .first() on that test ID without visibility filtering.
 */
@Slf4j
final class VintedCatalogSearchInputResolver {

    private static final String SEARCH_TEST_ID_SELECTOR =
            "[data-testid='search-text--input']";

    // Compatibility path for older Vinted layouts that lack the test ID.
    private static final String LEGACY_SEARCH_SELECTOR =
            "form[action='/catalog'] input[name='search_text']";

    private VintedCatalogSearchInputResolver() {
    }

    static Locator resolveVisible(Page page) {
        Locator confirmedHeaderInput = page.locator(
                "header " + SEARCH_TEST_ID_SELECTOR + ":visible"
        );

        if (confirmedHeaderInput.count() > 0) {
            log.debug("[FILTER SEARCH] Using visible header input by confirmed data-testid.");
            return confirmedHeaderInput.first();
        }

        Locator confirmedVisibleInputs = page.locator(
                SEARCH_TEST_ID_SELECTOR + ":visible"
        );

        if (confirmedVisibleInputs.count() > 0) {
            log.debug("[FILTER SEARCH] Using visible catalog input by confirmed data-testid.");
            return confirmedVisibleInputs.first();
        }

        // Do not delete old selectors until other Vinted UI variants have been
        // observed and tested; there may be pages without the current test ID.
        Locator legacyHeaderInputs = page.locator(
                "header " + LEGACY_SEARCH_SELECTOR + ":visible"
        );

        if (legacyHeaderInputs.count() > 0) {
            log.info("[FILTER SEARCH] Confirmed search test ID missing. "
                    + "Using visible legacy header /catalog input.");
            return legacyHeaderInputs.first();
        }

        Locator legacyVisibleInputs = page.locator(
                LEGACY_SEARCH_SELECTOR + ":visible"
        );
        int count = legacyVisibleInputs.count();
        if (count > 0) {
            log.info("[FILTER SEARCH] Confirmed search test ID missing. "
                    + "Using visible legacy /catalog input. matches={}", count);
            return legacyVisibleInputs.first();
        }

        int allLegacyInputs = page.locator(LEGACY_SEARCH_SELECTOR).count();
        throw new IllegalStateException(
                "Vinted search input is not visible. "
                        + "Matching /catalog search inputs in DOM: "
                        + allLegacyInputs
        );
    }
}

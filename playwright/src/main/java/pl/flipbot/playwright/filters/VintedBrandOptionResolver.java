package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolve Vinted brand options using the exact, visible native brand label.
 *
 * Observed DOM: selectable-item-brand-109048 (role=button) and
 * selectable-item-brand-109048--title ("Samsung"). The numeric collection
 * ID is derived from the current DOM, never hardcoded as a brand mapping.
 */
final class VintedBrandOptionResolver {

    private static final String BRAND_TITLE_SELECTOR =
            "[data-testid^='selectable-item-brand-'][data-testid$='--title']";
    private static final Pattern BRAND_TITLE_TEST_ID =
            Pattern.compile("^selectable-item-brand-(\\d+)--title$");

    private VintedBrandOptionResolver() {
    }

    static Locator resolve(Page page, String brand) {
        if (brand == null || brand.isBlank()) {
            throw new IllegalArgumentException("Brand cannot be blank");
        }

        Locator exactRow = findExactVisibleBrandRow(page, brand);
        if (exactRow != null) {
            return exactRow;
        }

        /*
         * Compatibility path: Vinted may render a different brand picker.
         * Use an ANCHORED accessible name: "Samsung" must not match
         * "Samsungite", "Samsonite", or other similarly named brands.
         */
        return page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(exactLabelPattern(brand))
        );
    }

    static Locator findExactVisibleBrandRow(Page page, String brand) {
        Locator titleCandidates = page.locator(BRAND_TITLE_SELECTOR).filter(
                new Locator.FilterOptions().setHasText(exactLabelPattern(brand))
        );

        int candidateCount = titleCandidates.count();
        Locator exactRow = null;
        String resolvedBrandId = null;

        for (int index = 0; index < candidateCount; index++) {
            Locator title = titleCandidates.nth(index);
            if (!title.isVisible()
                    || !normalized(brand).equalsIgnoreCase(normalized(title.innerText()))) {
                continue;
            }

            Matcher matcher = BRAND_TITLE_TEST_ID.matcher(
                    String.valueOf(title.getAttribute("data-testid"))
            );
            if (!matcher.matches()) {
                continue;
            }

            String brandId = matcher.group(1);
            Locator rows = page.getByTestId("selectable-item-brand-" + brandId);
            int rowCount = rows.count();
            for (int rowIndex = 0; rowIndex < rowCount; rowIndex++) {
                Locator row = rows.nth(rowIndex);
                if (!row.isVisible()
                        || !"button".equalsIgnoreCase(row.getAttribute("role"))) {
                    continue;
                }

                if (resolvedBrandId != null && !resolvedBrandId.equals(brandId)) {
                    throw new IllegalStateException(
                            "Multiple distinct visible Vinted brand IDs share the exact "
                                    + "label '" + brand + "'. Refusing ambiguous selection."
                    );
                }

                resolvedBrandId = brandId;
                exactRow = row;
            }
        }

        return exactRow;
    }

    static Pattern exactLabelPattern(String label) {
        return Pattern.compile(
                "^\\s*" + Pattern.quote(normalized(label)) + "\\s*$",
                Pattern.CASE_INSENSITIVE
        );
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}

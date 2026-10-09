package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import java.util.regex.Pattern;

/**
 * Resolve category options by their current native Vinted DOM id and exact
 * visible label, rather than a broad page-wide text lookup.
 *
 * Verified open category-picker roots have role=button and
 * id=catalog_ids-list-item-<numeric id>. Unlike brand/model rows these
 * category entries have NO data-testid. Never invent one or hardcode ids.
 */
final class VintedCategoryOptionResolver {

    private static final String CATEGORY_ROWS_SELECTOR =
            "[id^='catalog_ids-list-item-'][role='button']";

    private static final Pattern CATEGORY_ROW_ID =
            Pattern.compile("^catalog_ids-list-item-\\d+$");

    private VintedCategoryOptionResolver() {
    }

    static Locator resolve(Page page, String category) {
        if (category == null || category.isBlank()) {
            throw new IllegalArgumentException("Category cannot be blank");
        }

        Locator exactRow = findExactVisibleCategoryRow(page, category);
        if (exactRow != null) {
            return exactRow;
        }

        /*
         * Compatibility for different Vinted category widgets. Anchor the
         * accessible name, so selecting 'Dom' cannot match unrelated labels.
         * The caller retains its existing retry and URL persistence checks.
         */
        return page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName(exactLabelPattern(category))
        );
    }

    static Locator findExactVisibleCategoryRow(Page page, String category) {
        Locator rows = page.locator(CATEGORY_ROWS_SELECTOR).filter(
                new Locator.FilterOptions().setHasText(exactLabelPattern(category))
        );

        Locator matchedRow = null;
        String matchedId = null;
        int count = rows.count();

        for (int index = 0; index < count; index++) {
            Locator row = rows.nth(index);
            if (!row.isVisible()
                    || !normalized(category).equalsIgnoreCase(normalized(row.innerText()))) {
                continue;
            }

            String id = row.getAttribute("id");
            if (id == null || !CATEGORY_ROW_ID.matcher(id).matches()
                    || !"button".equalsIgnoreCase(row.getAttribute("role"))) {
                continue;
            }

            if (matchedId != null && !matchedId.equals(id)) {
                throw new IllegalStateException(
                        "Multiple distinct visible category rows share exact label '"
                                + category + "'. Refusing ambiguous selection."
                );
            }

            matchedId = id;
            matchedRow = row;
        }

        return matchedRow;
    }

    static Pattern exactLabelPattern(String name) {
        return Pattern.compile(
                "^\\s*" + Pattern.quote(normalized(name)) + "\\s*$",
                Pattern.CASE_INSENSITIVE
        );
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ");
    }
}

package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verified expanded root category-picker DOM: nine category rows have no
 * data-testid, but do have role=button and numeric catalog_ids-list-item IDs.
 * The earlier "Elektronika" result came from a page breadcrumb; a separate
 * category-row-only probe later confirmed catalog_ids-list-item-2994.
 * Observed IDs are fixtures only, not a production category-ID mapping.
 */
public class VintedCategoryOptionResolverTest {

    private static final String ROW_SELECTOR =
            "[id^='catalog_ids-list-item-'][role='button']";

    @Test
    public void observedRootCategoryRowsResolveFromExactNameAndNativeId() {
        String[][] observed = {
                {"Kobiety", "1904"},
                {"Mężczyźni", "5"},
                {"Przedmioty designerskie", "2993"},
                {"Dzieci", "1193"},
                {"Dom", "1918"},
                {"Elektronika", "2994"},
                {"Książki i multimedia", "2309"},
                {"Hobby i kolekcjonerstwo", "4824"},
                {"Sport", "4332"}
        };

        for (String[] pair : observed) {
            Page page = mock(Page.class);
            Locator allRows = mock(Locator.class);
            Locator filteredRows = mock(Locator.class);
            Locator row = mock(Locator.class);
            when(page.locator(ROW_SELECTOR)).thenReturn(allRows);
            when(allRows.filter(any(Locator.FilterOptions.class))).thenReturn(filteredRows);
            when(filteredRows.count()).thenReturn(1);
            when(filteredRows.nth(0)).thenReturn(row);
            when(row.isVisible()).thenReturn(true);
            when(row.innerText()).thenReturn(pair[0]);
            when(row.getAttribute("id")).thenReturn("catalog_ids-list-item-" + pair[1]);
            when(row.getAttribute("role")).thenReturn("button");

            assertSame(row, VintedCategoryOptionResolver.resolve(page, pair[0]));
            verify(page).locator(ROW_SELECTOR);
        }
    }

    @Test
    public void exactNameNeverAcceptsSimilarOrLongerLabels() {
        Pattern pattern = VintedCategoryOptionResolver.exactLabelPattern("Dom");
        assertTrue(pattern.matcher("Dom").matches());
        assertTrue(pattern.matcher("  DOM  ").matches());
        assertFalse(pattern.matcher("Dom i ogród").matches());
        assertFalse(pattern.matcher("Domowe dekoracje").matches());

        Pattern design = VintedCategoryOptionResolver.exactLabelPattern(
                "Przedmioty designerskie"
        );
        assertFalse(design.matcher("Przedmioty designerskie i vintage").matches());
    }

    @Test
    public void breadcrumbDoesNotQualifyAsCategoryRow() {
        Page page = mock(Page.class);
        Locator allRows = mock(Locator.class);
        Locator matches = mock(Locator.class);
        when(page.locator(ROW_SELECTOR)).thenReturn(allRows);
        when(allRows.filter(any(Locator.FilterOptions.class))).thenReturn(matches);
        when(matches.count()).thenReturn(0);

        // A broad page-wide text search once found "Elektronika" only in
        // breadcrumbs (<a> in <li>), not in the category picker. A breadcrumb
        // alone is never evidence that the category-row locator is present.
        // Its actual root-row ID 2994 was confirmed in a separate later probe.
        assertNull(VintedCategoryOptionResolver.findExactVisibleCategoryRow(
                page, "Elektronika"
        ));
    }

    @Test
    public void maliciousOrIncorrectBrowserCandidateNeverBecomesIdentityProof() {
        Page page = mock(Page.class);
        Locator allRows = mock(Locator.class);
        Locator matches = mock(Locator.class);
        Locator row = mock(Locator.class);
        when(page.locator(ROW_SELECTOR)).thenReturn(allRows);
        when(allRows.filter(any(Locator.FilterOptions.class))).thenReturn(matches);
        when(matches.count()).thenReturn(1);
        when(matches.nth(0)).thenReturn(row);
        when(row.isVisible()).thenReturn(true);
        when(row.innerText()).thenReturn("Dom i ogród");
        when(row.getAttribute("id")).thenReturn("catalog_ids-list-item-1918");

        assertNull(VintedCategoryOptionResolver.findExactVisibleCategoryRow(
                page, "Dom"
        ));
    }

    @Test
    public void hiddenRowAndWrongIdNeverQualifyAsVisibleCategory() {
        Page page = mock(Page.class);
        Locator allRows = mock(Locator.class);
        Locator matches = mock(Locator.class);
        Locator hidden = mock(Locator.class);
        Locator wrongId = mock(Locator.class);
        when(page.locator(ROW_SELECTOR)).thenReturn(allRows);
        when(allRows.filter(any(Locator.FilterOptions.class))).thenReturn(matches);
        when(matches.count()).thenReturn(2);

        when(matches.nth(0)).thenReturn(hidden);
        when(hidden.isVisible()).thenReturn(false);
        when(hidden.innerText()).thenReturn("Dzieci");

        when(matches.nth(1)).thenReturn(wrongId);
        when(wrongId.isVisible()).thenReturn(true);
        when(wrongId.innerText()).thenReturn("Dzieci");
        when(wrongId.getAttribute("id")).thenReturn("catalog_ids-list-item-danger");

        assertNull(VintedCategoryOptionResolver.findExactVisibleCategoryRow(
                page, "Dzieci"
        ));
    }

    @Test
    public void identicalLabelsWithDistinctIdsFailClosed() {
        Page page = mock(Page.class);
        Locator allRows = mock(Locator.class);
        Locator matches = mock(Locator.class);
        when(page.locator(ROW_SELECTOR)).thenReturn(allRows);
        when(allRows.filter(any(Locator.FilterOptions.class))).thenReturn(matches);
        when(matches.count()).thenReturn(2);

        for (int i = 0; i < 2; i++) {
            Locator row = mock(Locator.class);
            when(matches.nth(i)).thenReturn(row);
            when(row.isVisible()).thenReturn(true);
            when(row.innerText()).thenReturn("Dom");
            when(row.getAttribute("id")).thenReturn(
                    "catalog_ids-list-item-" + (i == 0 ? "1918" : "999999")
            );
            when(row.getAttribute("role")).thenReturn("button");
        }

        IllegalStateException e = assertThrows(
                IllegalStateException.class,
                () -> VintedCategoryOptionResolver.resolve(page, "Dom")
        );
        assertTrue(e.getMessage().contains("Multiple distinct"));
    }

    @Test
    public void legacyWidgetFallbackUsesAnchoredAccessibleName() {
        Page page = mock(Page.class);
        Locator allRows = mock(Locator.class);
        Locator matches = mock(Locator.class);
        Locator fallback = mock(Locator.class);
        when(page.locator(ROW_SELECTOR)).thenReturn(allRows);
        when(allRows.filter(any(Locator.FilterOptions.class))).thenReturn(matches);
        when(matches.count()).thenReturn(0);
        when(page.getByRole(eq(AriaRole.BUTTON), any(Page.GetByRoleOptions.class)))
                .thenReturn(fallback);

        assertSame(fallback, VintedCategoryOptionResolver.resolve(page, "Elektronika"));
        verify(page).getByRole(eq(AriaRole.BUTTON), any(Page.GetByRoleOptions.class));
    }

    @Test
    public void blankCategoryFailsBeforeAnyDomLookup() {
        Page page = mock(Page.class);
        assertThrows(IllegalArgumentException.class,
                () -> VintedCategoryOptionResolver.resolve(page, " "));
    }
}

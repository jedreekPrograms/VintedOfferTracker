package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The user confirmed real Vinted URL catalog[]=3661 after choosing
 * Electronics > Phones/communication > Mobile phones. Test the runtime
 * provenance chain (visible native row -> clicked ID -> final URL), not just
 * existence of any catalog[] parameter.
 */
public class FilterActionsExactCategoryPersistenceTest {

    private static final String CATEGORY_SELECTOR =
            "[id^='catalog_ids-list-item-'][role='button']";

    private FilterActions actionsWithVerifiedPhoneRow(Page page) {
        when(page.getByTestId(FilterSelectors.CATEGORY_FILTER))
                .thenReturn(mock(Locator.class));

        Locator allRows = mock(Locator.class);
        Locator candidates = mock(Locator.class);
        Locator phoneRow = mock(Locator.class);
        when(page.locator(CATEGORY_SELECTOR)).thenReturn(allRows);
        when(allRows.filter(any(Locator.FilterOptions.class)))
                .thenReturn(candidates);
        when(candidates.count()).thenReturn(1);
        when(candidates.nth(0)).thenReturn(phoneRow);
        when(phoneRow.isVisible()).thenReturn(true);
        when(phoneRow.innerText()).thenReturn("Telefony komórkowe");
        when(phoneRow.getAttribute("role")).thenReturn("button");
        when(phoneRow.getAttribute("id"))
                .thenReturn("catalog_ids-list-item-3661");

        FilterActions actions = new FilterActions(page);
        actions.openFilter(FilterSelectors.CATEGORY_FILTER);
        actions.selectOption("Telefony komórkowe");
        verify(phoneRow).click();
        return actions;
    }

    @Test
    public void matchingExactPhoneCatalogIdIsAccepted() {
        Page page = mock(Page.class);
        when(page.url()).thenReturn(
                "https://www.vinted.pl/catalog?catalog%5B%5D=3661"
        );

        FilterActions actions = actionsWithVerifiedPhoneRow(page);

        assertTrue(actions.waitForSelectedCategoryPersisted(200));
    }

    @Test
    public void differentCatalogIdIsRejectedEvenThoughParameterExists() {
        Page page = mock(Page.class);
        when(page.url()).thenReturn(
                "https://www.vinted.pl/catalog?catalog%5B%5D=3662"
        );

        FilterActions actions = actionsWithVerifiedPhoneRow(page);

        assertFalse(actions.waitForSelectedCategoryPersisted(25));
    }

    @Test
    public void legacyUiWithoutVerifiedRowPreservesPresenceFallback() {
        Page page = mock(Page.class);
        when(page.url()).thenReturn(
                "https://www.vinted.pl/catalog?catalog%5B%5D=3661"
        );

        FilterActions actions = new FilterActions(page);

        assertTrue(actions.waitForSelectedCategoryPersisted(200));
    }
}

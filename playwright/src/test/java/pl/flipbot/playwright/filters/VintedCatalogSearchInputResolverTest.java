package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The observed Vinted DOM contains two search-text--input fields.
 * Prefer the visible header; support responsive layouts; retain old selectors.
 */
public class VintedCatalogSearchInputResolverTest {

    private static final String HEADER_TEST_ID =
            "header [data-testid='search-text--input']:visible";
    private static final String VISIBLE_TEST_ID =
            "[data-testid='search-text--input']:visible";
    private static final String HEADER_LEGACY =
            "header form[action='/catalog'] input[name='search_text']:visible";
    private static final String VISIBLE_LEGACY =
            "form[action='/catalog'] input[name='search_text']:visible";
    private static final String ALL_LEGACY =
            "form[action='/catalog'] input[name='search_text']";

    @Test
    public void exactVisibleHeaderTestIdHasFirstPriority() {
        Page page = mock(Page.class);
        Locator header = mock(Locator.class);
        Locator selected = mock(Locator.class);
        when(page.locator(HEADER_TEST_ID)).thenReturn(header);
        when(header.count()).thenReturn(1);
        when(header.first()).thenReturn(selected);

        assertSame(selected, VintedCatalogSearchInputResolver.resolveVisible(page));
        verify(page).locator(HEADER_TEST_ID);
    }

    @Test
    public void responsiveSearchInputUsedWhenHeaderSearchIsHidden() {
        Page page = mock(Page.class);
        Locator hiddenHeader = mock(Locator.class);
        Locator visible = mock(Locator.class);
        Locator selected = mock(Locator.class);
        when(page.locator(HEADER_TEST_ID)).thenReturn(hiddenHeader);
        when(hiddenHeader.count()).thenReturn(0);
        when(page.locator(VISIBLE_TEST_ID)).thenReturn(visible);
        when(visible.count()).thenReturn(1);
        when(visible.first()).thenReturn(selected);

        assertSame(selected, VintedCatalogSearchInputResolver.resolveVisible(page));
    }

    @Test
    public void legacyHeaderSelectorRemainsWhenTestIdMissing() {
        Page page = mock(Page.class);
        Locator noHeaderId = mock(Locator.class);
        Locator noVisibleId = mock(Locator.class);
        Locator legacyHeader = mock(Locator.class);
        Locator selected = mock(Locator.class);
        when(page.locator(HEADER_TEST_ID)).thenReturn(noHeaderId);
        when(page.locator(VISIBLE_TEST_ID)).thenReturn(noVisibleId);
        when(page.locator(HEADER_LEGACY)).thenReturn(legacyHeader);
        when(legacyHeader.count()).thenReturn(1);
        when(legacyHeader.first()).thenReturn(selected);

        assertSame(selected, VintedCatalogSearchInputResolver.resolveVisible(page));
    }

    @Test
    public void legacyResponsiveSelectorRemainsWhenHeaderMissing() {
        Page page = mock(Page.class);
        when(page.locator(HEADER_TEST_ID)).thenReturn(mock(Locator.class));
        when(page.locator(VISIBLE_TEST_ID)).thenReturn(mock(Locator.class));
        when(page.locator(HEADER_LEGACY)).thenReturn(mock(Locator.class));
        Locator legacyResponsive = mock(Locator.class);
        Locator selected = mock(Locator.class);
        when(page.locator(VISIBLE_LEGACY)).thenReturn(legacyResponsive);
        when(legacyResponsive.count()).thenReturn(2);
        when(legacyResponsive.first()).thenReturn(selected);

        assertSame(selected, VintedCatalogSearchInputResolver.resolveVisible(page));
    }

    @Test
    public void neverSelectsAnInvisibleSearchInput() {
        Page page = mock(Page.class);
        when(page.locator(HEADER_TEST_ID)).thenReturn(mock(Locator.class));
        when(page.locator(VISIBLE_TEST_ID)).thenReturn(mock(Locator.class));
        when(page.locator(HEADER_LEGACY)).thenReturn(mock(Locator.class));
        when(page.locator(VISIBLE_LEGACY)).thenReturn(mock(Locator.class));
        Locator all = mock(Locator.class);
        when(page.locator(ALL_LEGACY)).thenReturn(all);
        when(all.count()).thenReturn(2);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> VintedCatalogSearchInputResolver.resolveVisible(page)
        );
        assertEquals(
                "Vinted search input is not visible. "
                        + "Matching /catalog search inputs in DOM: 2",
                failure.getMessage()
        );
    }
}

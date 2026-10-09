package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.Test;

import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Captured brand-picker DOM (October 2026): Samsung 109048, Samsonite 26963,
 * Sass & Belle 482197, SAM & JO 1034997, Samson 191894, Sass & Bide 186294,
 * Sam & Libby 274001, Disney x Samsonite 7136871, Sam & Lili 109246 and
 * Sass & Me 4892497. Every brand has row, --title and --suffix test IDs.
 */
public class VintedBrandOptionResolverTest {

    @Test
    public void selectsExactObservedSamsungRowFromItsTitleTestId() {
        Page page = mock(Page.class);
        Locator titles = mock(Locator.class);
        Locator matchedTitles = mock(Locator.class);
        Locator title = mock(Locator.class);
        Locator canonicalRows = mock(Locator.class);
        Locator row = mock(Locator.class);

        when(page.locator("[data-testid^='selectable-item-brand-'][data-testid$='--title']"))
                .thenReturn(titles);
        when(titles.filter(any(Locator.FilterOptions.class))).thenReturn(matchedTitles);
        when(matchedTitles.count()).thenReturn(1);
        when(matchedTitles.nth(0)).thenReturn(title);
        when(title.isVisible()).thenReturn(true);
        when(title.innerText()).thenReturn("Samsung");
        when(title.getAttribute("data-testid"))
                .thenReturn("selectable-item-brand-109048--title");
        when(page.getByTestId("selectable-item-brand-109048")).thenReturn(canonicalRows);
        when(canonicalRows.count()).thenReturn(1);
        when(canonicalRows.nth(0)).thenReturn(row);
        when(row.isVisible()).thenReturn(true);
        when(row.getAttribute("role")).thenReturn("button");

        assertSame(row, VintedBrandOptionResolver.resolve(page, "Samsung"));
        verify(page).getByTestId("selectable-item-brand-109048");
    }

    @Test
    public void strictPatternRejectsAdjacentObservedBrands() {
        Pattern samsung = VintedBrandOptionResolver.exactLabelPattern("Samsung");
        assertTrue(samsung.matcher(" Samsung ").matches());
        assertTrue(samsung.matcher("SAMSUNG").matches());

        for (String different : new String[] {
                "Samsonite", "Sass & Belle", "SAM & JO", "Samson",
                "Sass & Bide", "Sam & Libby", "Disney x Samsonite",
                "Sam & Lili", "Sass & Me", "Samsungite"
        }) {
            assertFalse(different, samsung.matcher(different).matches());
        }
    }

    @Test
    public void ignoresIncorrectTitleEvenIfBrowserReturnsItAsCandidate() {
        Page page = mock(Page.class);
        Locator titles = mock(Locator.class);
        Locator matches = mock(Locator.class);
        Locator wrong = mock(Locator.class);
        when(page.locator(any(String.class))).thenReturn(titles);
        when(titles.filter(any(Locator.FilterOptions.class))).thenReturn(matches);
        when(matches.count()).thenReturn(1);
        when(matches.nth(0)).thenReturn(wrong);
        when(wrong.isVisible()).thenReturn(true);
        when(wrong.innerText()).thenReturn("Samsonite");
        when(wrong.getAttribute("data-testid"))
                .thenReturn("selectable-item-brand-26963--title");

        assertNull(VintedBrandOptionResolver.findExactVisibleBrandRow(page, "Samsung"));
    }

    @Test
    public void hiddenOrSuffixNodeNeverProvesBrandIdentity() {
        Page page = mock(Page.class);
        Locator titles = mock(Locator.class);
        Locator matches = mock(Locator.class);
        Locator hidden = mock(Locator.class);
        Locator suffix = mock(Locator.class);
        when(page.locator(any(String.class))).thenReturn(titles);
        when(titles.filter(any(Locator.FilterOptions.class))).thenReturn(matches);
        when(matches.count()).thenReturn(2);
        when(matches.nth(0)).thenReturn(hidden);
        when(hidden.isVisible()).thenReturn(false);
        when(matches.nth(1)).thenReturn(suffix);
        when(suffix.isVisible()).thenReturn(true);
        when(suffix.innerText()).thenReturn("Samsung");
        when(suffix.getAttribute("data-testid"))
                .thenReturn("selectable-item-brand-109048--suffix");

        assertNull(VintedBrandOptionResolver.findExactVisibleBrandRow(page, "Samsung"));
    }

    @Test
    public void whenNoExactTestIdIsVisibleRetainsAnchoredRoleFallback() {
        Page page = mock(Page.class);
        Locator titles = mock(Locator.class);
        Locator matches = mock(Locator.class);
        Locator fallback = mock(Locator.class);
        when(page.locator(any(String.class))).thenReturn(titles);
        when(titles.filter(any(Locator.FilterOptions.class))).thenReturn(matches);
        when(matches.count()).thenReturn(0);
        when(page.getByRole(eq(AriaRole.BUTTON), any(Page.GetByRoleOptions.class)))
                .thenReturn(fallback);

        assertSame(fallback, VintedBrandOptionResolver.resolve(page, "Samsung"));
        verify(page).getByRole(eq(AriaRole.BUTTON), any(Page.GetByRoleOptions.class));
    }

    @Test
    public void refusesToSelectTwoDistinctIdsForIdenticalVisibleName() {
        Page page = mock(Page.class);
        Locator titles = mock(Locator.class);
        Locator matches = mock(Locator.class);
        when(page.locator(any(String.class))).thenReturn(titles);
        when(titles.filter(any(Locator.FilterOptions.class))).thenReturn(matches);
        when(matches.count()).thenReturn(2);

        for (int i = 0; i < 2; i++) {
            String id = i == 0 ? "109048" : "999999";
            Locator title = mock(Locator.class);
            Locator rows = mock(Locator.class);
            Locator row = mock(Locator.class);
            when(matches.nth(i)).thenReturn(title);
            when(title.isVisible()).thenReturn(true);
            when(title.innerText()).thenReturn("Samsung");
            when(title.getAttribute("data-testid"))
                    .thenReturn("selectable-item-brand-" + id + "--title");
            when(page.getByTestId("selectable-item-brand-" + id)).thenReturn(rows);
            when(rows.count()).thenReturn(1);
            when(rows.nth(0)).thenReturn(row);
            when(row.isVisible()).thenReturn(true);
            when(row.getAttribute("role")).thenReturn("button");
        }

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> VintedBrandOptionResolver.resolve(page, "Samsung")
        );
        assertTrue(failure.getMessage().contains("Multiple distinct"));
    }

    @Test
    public void blankBrandFailsBeforeAnyDomOperation() {
        Page page = mock(Page.class);
        assertThrows(IllegalArgumentException.class,
                () -> VintedBrandOptionResolver.resolve(page, " "));
    }
}

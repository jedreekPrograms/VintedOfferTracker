package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Browser-side exact title matching is an optimization only.
 * The Java identity check must still reject a wrong model if the browser
 * reports an unexpected candidate; the legacy scan can then take over.
 */
public class VintedFastExactModelTitleTest {

    @Test
    public void browserMatchedCanonicalTitleIsReturned() {
        FilterActions filter = new FilterActions(mock(Page.class));
        Locator titles = mock(Locator.class);
        Locator browserMatches = mock(Locator.class);
        Locator exact = mock(Locator.class);
        when(titles.filter(any(Locator.FilterOptions.class)))
                .thenReturn(browserMatches);
        when(browserMatches.count()).thenReturn(1);
        when(browserMatches.nth(0)).thenReturn(exact);
        when(exact.isVisible()).thenReturn(true);
        when(exact.innerText()).thenReturn("Galaxy S25 FE");

        assertSame(exact, filter.findFastExactModelTitle(titles, "Galaxy S25 FE"));
    }

    @Test
    public void mismatchedModelIsNeverAcceptedEvenIfPlaywrightReturnsIt() {
        FilterActions filter = new FilterActions(mock(Page.class));
        Locator titles = mock(Locator.class);
        Locator browserMatches = mock(Locator.class);
        when(titles.filter(any(Locator.FilterOptions.class)))
                .thenReturn(browserMatches);
        when(browserMatches.count()).thenReturn(4);

        String[] wrongModels = {
                "Galaxy S25 Edge",
                "Galaxy S25 FE",
                "Galaxy S25 Ultra",
                "Galaxy S25+"
        };
        for (int i = 0; i < wrongModels.length; i++) {
            Locator candidate = mock(Locator.class);
            when(browserMatches.nth(i)).thenReturn(candidate);
            when(candidate.isVisible()).thenReturn(true);
            when(candidate.innerText()).thenReturn(wrongModels[i]);
        }

        assertNull(filter.findFastExactModelTitle(titles, "Galaxy S25"));
    }

    @Test
    public void hiddenCandidateIsNotConsideredProof() {
        FilterActions filter = new FilterActions(mock(Page.class));
        Locator titles = mock(Locator.class);
        Locator browserMatches = mock(Locator.class);
        Locator hidden = mock(Locator.class);
        when(titles.filter(any(Locator.FilterOptions.class)))
                .thenReturn(browserMatches);
        when(browserMatches.count()).thenReturn(1);
        when(browserMatches.nth(0)).thenReturn(hidden);
        when(hidden.isVisible()).thenReturn(false);
        when(hidden.innerText()).thenReturn("Galaxy S25");

        assertNull(filter.findFastExactModelTitle(titles, "Galaxy S25"));
    }

    @Test
    public void missingTestIdTitleDoesNotPretendModelExists() {
        FilterActions filter = new FilterActions(mock(Page.class));
        Locator titles = mock(Locator.class);
        Locator browserMatches = mock(Locator.class);
        when(titles.filter(any(Locator.FilterOptions.class)))
                .thenReturn(browserMatches);
        when(browserMatches.count()).thenReturn(0);

        assertNull(filter.findFastExactModelTitle(titles, "Galaxy S25"));
    }

    @Test
    public void exactTitleDoesNotAcceptCountMetadataInFastPath() {
        FilterActions filter = new FilterActions(mock(Page.class));
        Locator titles = mock(Locator.class);
        Locator browserMatches = mock(Locator.class);
        Locator withMetadata = mock(Locator.class);
        when(titles.filter(any(Locator.FilterOptions.class)))
                .thenReturn(browserMatches);
        when(browserMatches.count()).thenReturn(1);
        when(browserMatches.nth(0)).thenReturn(withMetadata);
        when(withMetadata.isVisible()).thenReturn(true);
        when(withMetadata.innerText()).thenReturn("Galaxy S25\n11 przedmiotów");

        assertNull(filter.findFastExactModelTitle(titles, "Galaxy S25"));
        // Existing row-by-row compatibility path handles metadata separately.
    }
}

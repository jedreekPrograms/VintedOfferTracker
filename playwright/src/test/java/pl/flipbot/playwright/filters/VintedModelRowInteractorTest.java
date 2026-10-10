package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class VintedModelRowInteractorTest {
    @Test
    public void canonicalVisibleRowWithExactIdIsKeptRatherThanGlobalFirst() {
        Page page = mock(Page.class);
        Locator evidence = mock(Locator.class);
        when(evidence.getAttribute("data-testid"))
                .thenReturn("selectable-item-brand_collection-9977");
        when(evidence.isVisible()).thenReturn(true);
        assertSame(evidence, new VintedModelRowInteractor(page)
                .canonicalModelRow("9977", evidence));
        verify(page, never()).getByTestId(anyString());
    }

    @Test
    public void choosesVisibleCanonicalRowWhenEvidenceIsOnlyChildTitle() {
        Page page = mock(Page.class);
        Locator evidence = mock(Locator.class), rows = mock(Locator.class);
        Locator first = mock(Locator.class), second = mock(Locator.class);
        when(evidence.getAttribute("data-testid")).thenReturn(
                "selectable-item-brand_collection-9977--title");
        when(page.getByTestId("selectable-item-brand_collection-9977")).thenReturn(rows);
        when(rows.count()).thenReturn(2);
        when(rows.nth(0)).thenReturn(first);
        when(first.isVisible()).thenReturn(false);
        when(rows.nth(1)).thenReturn(second);
        when(second.isVisible()).thenReturn(true);

        assertSame(second, new VintedModelRowInteractor(page)
                .canonicalModelRow("9977", evidence));
    }

    @Test
    public void rejectsInvalidModelIdBeforeGeneratingAnySelector() {
        assertThrows(IllegalArgumentException.class,
                () -> VintedModelRowInteractor.exactModelSuffixTestId("9977'"));
        assertThrows(IllegalArgumentException.class,
                () -> VintedModelRowInteractor.exactModelCheckboxSelector("S25"));
    }

    @Test
    public void alreadyCheckedExactNativeInputPreventsExtraClicks() {
        Page page = mock(Page.class);
        Locator row = mock(Locator.class), all = mock(Locator.class);
        Locator checkbox = mock(Locator.class);
        when(row.locator("input[type='checkbox'][name='brand_collection_ids[]'][value='9977']"))
                .thenReturn(all);
        when(all.first()).thenReturn(checkbox);
        when(checkbox.count()).thenReturn(1);
        when(checkbox.isChecked()).thenReturn(true);

        new VintedModelRowInteractor(page).selectExactModelRow(row, "Galaxy S25 FE", "9977");
        verify(row, never()).click(any(Locator.ClickOptions.class));
        verify(page, never()).getByTestId(anyString());
    }

    @Test
    public void exactSuffixClickThenCheckboxConfirmationStopsOtherFallbacks() {
        Page page = mock(Page.class);
        Locator row = mock(Locator.class), boxes = mock(Locator.class);
        Locator checkbox = mock(Locator.class), suffixes = mock(Locator.class);
        Locator suffix = mock(Locator.class);
        when(row.locator("input[type='checkbox'][name='brand_collection_ids[]'][value='9977']"))
                .thenReturn(boxes);
        when(boxes.first()).thenReturn(checkbox);
        when(checkbox.count()).thenReturn(1);
        when(checkbox.isChecked()).thenReturn(false,true);
        when(row.locator("[data-testid='selectable-item-brand_collection-9977--suffix']"))
                .thenReturn(suffixes);
        when(suffixes.first()).thenReturn(suffix);
        when(suffix.count()).thenReturn(1);
        when(suffix.isVisible()).thenReturn(true);
        new VintedModelRowInteractor(page).selectExactModelRow(row,"Galaxy S25 FE","9977");
        verify(suffix).click(any(Locator.ClickOptions.class));
        verify(row, never()).click(any(Locator.ClickOptions.class));
    }
}

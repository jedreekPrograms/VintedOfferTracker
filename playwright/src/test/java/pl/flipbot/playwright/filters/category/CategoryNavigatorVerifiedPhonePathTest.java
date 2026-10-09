package pl.flipbot.playwright.filters.category;

import org.junit.Test;
import org.mockito.InOrder;
import pl.flipbot.playwright.filters.FilterActions;
import pl.flipbot.playwright.filters.FilterSelectors;

import java.util.List;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Regression guard for the three-level navigation captured from a live Vinted
 * category picker. The navigator selects names in order; the resolver derives
 * native row ids from each current menu and the URL persistence check remains.
 */
public class CategoryNavigatorVerifiedPhonePathTest {

    @Test
    public void appliesObservedElectronicsToPhonesPathInTheCorrectOrder() {
        FilterActions actions = mock(FilterActions.class);
        when(actions.waitForSelectedCategoryPersisted(5_000))
                .thenReturn(true);

        CategoryNavigator navigator = new CategoryNavigator(actions);
        navigator.select(List.of(
                "Elektronika",
                "Telefony komórkowe i komunikacja",
                "Telefony komórkowe"
        ));

        InOrder order = inOrder(actions);
        order.verify(actions).openFilter(FilterSelectors.CATEGORY_FILTER);
        order.verify(actions).waitForOption("Elektronika", 10_000);
        order.verify(actions).selectOption("Elektronika");
        order.verify(actions).waitForOption("Telefony komórkowe i komunikacja", 10_000);
        order.verify(actions).selectOption("Telefony komórkowe i komunikacja");
        order.verify(actions).waitForOption("Telefony komórkowe", 10_000);
        order.verify(actions).selectOption("Telefony komórkowe");
        order.verify(actions).waitForSelectedCategoryPersisted(5_000);
        order.verifyNoMoreInteractions();
    }

    @Test
    public void successfulClickSequenceMustNotBeAcceptedIfLeafIdDidNotPersist() {
        FilterActions actions = mock(FilterActions.class);
        // The new check fails if the observed exact clicked category ID is
        // missing or the persisted catalog[] contains a different category.
        when(actions.waitForSelectedCategoryPersisted(5_000)).thenReturn(false);

        CategoryNavigator navigator = new CategoryNavigator(actions);

        org.junit.Assert.assertThrows(
                IllegalStateException.class,
                () -> navigator.select(List.of(
                        "Elektronika",
                        "Telefony komórkowe i komunikacja",
                        "Telefony komórkowe"
                ))
        );

        // Retrying is still retained; it never silently declares success
        // merely because the catalog[] parameter exists with a wrong value.
        verify(actions, org.mockito.Mockito.times(3))
                .waitForSelectedCategoryPersisted(5_000);
    }

    @Test
    public void emptyCategoryPathDoesNotSelectAnything() {
        FilterActions actions = mock(FilterActions.class);
        new CategoryNavigator(actions).select(List.of());
        verifyNoInteractions(actions);
    }
}

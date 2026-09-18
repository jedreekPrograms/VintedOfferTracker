package pl.flipbot.playwright.filters.category;

import org.junit.Test;
import org.mockito.InOrder;
import pl.flipbot.playwright.filters.FilterActions;
import pl.flipbot.playwright.filters.FilterSelectors;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class CategoryNavigatorExactOptionTest {

    @Test
    public void categoryPathUsesExactButtonOptionsAndSettlesBetweenLevels() {
        FilterActions actions = mock(FilterActions.class);
        when(actions.waitForUrlParameterPresent(
                "catalog[]",
                5_000
        )).thenReturn(true);

        CategoryNavigator navigator =
                new CategoryNavigator(actions);

        navigator.select(
                List.of(
                        "Elektronika",
                        "Telefony komórkowe i komunikacja",
                        "Telefony komórkowe"
                )
        );

        InOrder order = inOrder(actions);
        order.verify(actions).openFilter(
                FilterSelectors.CATEGORY_FILTER
        );
        order.verify(actions).clickExactButtonOption(
                "Elektronika",
                10_000
        );
        order.verify(actions).waitForTimeout(350);
        order.verify(actions).clickExactButtonOption(
                "Telefony komórkowe i komunikacja",
                10_000
        );
        order.verify(actions).waitForTimeout(350);
        order.verify(actions).clickExactButtonOption(
                "Telefony komórkowe",
                10_000
        );
        order.verify(actions).waitForUrlParameterPresent(
                "catalog[]",
                5_000
        );

        verify(actions, never()).selectOption(anyString());
        verify(actions, never()).waitForOption(anyString(), anyDouble());
    }
}

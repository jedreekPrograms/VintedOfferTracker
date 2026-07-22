package pl.flipbot.playwright.filters.category;

import lombok.RequiredArgsConstructor;
import pl.flipbot.playwright.filters.FilterActions;
import pl.flipbot.playwright.filters.FilterSelectors;

import java.util.List;

@RequiredArgsConstructor
public class CategoryNavigator {

    private final FilterActions actions;

    public void select(List<String> categoryPath) {

        if (categoryPath == null || categoryPath.isEmpty()) {
            return;
        }

        actions.openFilter(
                FilterSelectors.CATEGORY_FILTER
        );

        for (int i = 0; i < categoryPath.size(); i++) {

            String category = categoryPath.get(i);

            actions.waitForOption(category);

            actions.selectOption(category);

        }

    }

}
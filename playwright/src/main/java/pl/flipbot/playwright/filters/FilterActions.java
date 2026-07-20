package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilterActions {

    private final Page page;

    public void openFilter(String filterTestId) {

        page.getByTestId(filterTestId)
                .click();

    }

    public void selectOption(String option) {

        page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName(option)
        ).click();

    }

    public void waitForOption(String option) {

        page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName(option)
        ).waitFor();

    }

    public void fillInput (String testId, String value) {

        Locator input = page.getByTestId(testId);

        input.fill(value);

    }

}

package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilterActions {

    private final Page page;

    public void openFilter(String filterTestId) {

        Locator filter = page.getByTestId(filterTestId);

        filter.waitFor();
        filter.click();

    }

    public void selectOption(String option) {

        Locator locator = page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName(option)
        );

        locator.waitFor();
        locator.click();

    }

    public void waitForOption(String option) {

        page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName(option)
        ).waitFor();

    }

    public void fillInput(String testId, String value) {

        Locator input = page.getByTestId(testId);

        input.waitFor();
        input.fill(value);

    }

    public void pressEnter() {

        page.keyboard().press("Enter");

    }

    public void clickSelector(String selector) {
        page.locator(selector).click();
    }
}
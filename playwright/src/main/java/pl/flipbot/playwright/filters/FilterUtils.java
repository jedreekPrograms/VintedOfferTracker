package pl.flipbot.playwright.filters;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

public final class FilterUtils {

    public static void clickOption(Page page, String option) {

        page.getByRole(
                AriaRole.BUTTON,
                new Page.GetByRoleOptions()
                        .setName(option)
        ).click();

    }

    public static void waitForVisible(Locator locator) {

        locator.waitFor();

    }
}

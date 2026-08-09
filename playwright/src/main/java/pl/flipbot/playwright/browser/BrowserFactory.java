package pl.flipbot.playwright.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.BrowserType;
public class BrowserFactory {

    private BrowserFactory() {}

    public static Browser createBrowser(
            Playwright playwright
    ) {

        return playwright.chromium()
                .launch(
                        new BrowserType.LaunchOptions()
                                .setHeadless(false)
                                .setChannel("chrome")
                );
    }
}

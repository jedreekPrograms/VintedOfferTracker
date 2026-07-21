package pl.flipbot.playwright.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Playwright;

public class BrowserFactory {

    private BrowserFactory() {}

    public static Browser createBrowser(Playwright playwright) {
        // Zmień localhost na dokładny adres IP 127.0.0.1
        return playwright.chromium().connectOverCDP("http://127.0.0.1:9222");
    }
}

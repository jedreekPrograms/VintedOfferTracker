package pl.flipbot.playwright.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

public class BrowserManager implements AutoCloseable {

    private final Playwright playwright;

    private final Browser browser;

    public BrowserManager() {

        this.playwright = Playwright.create();
        this.browser = BrowserFactory.createBrowser(playwright);
    }

    public Page newPage() {

        return browser.newPage();
    }

    @Override
    public void close() {

        browser.close();

        playwright.close();
    }
}

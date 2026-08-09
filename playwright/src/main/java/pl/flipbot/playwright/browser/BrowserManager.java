package pl.flipbot.playwright.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;

import java.nio.file.Path;

public class BrowserManager implements AutoCloseable {

    private final Playwright playwright;
    private final Browser browser;

    public BrowserManager() {
        this.playwright = Playwright.create();
        this.browser = BrowserFactory.createBrowser(playwright);
    }

    public BrowserContext createContext(Path storageState) {

        Browser.NewContextOptions options =
                new Browser.NewContextOptions();

        if (storageState != null) {

            options.setStorageStatePath(
                    storageState
            );
        }

        return browser.newContext(
                options
        );
    }

    @Override
    public void close() {
        // Przy połączeniu przez CDP zazwyczaj nie chcemy zamykać całej przeglądarki użytkownika,
        // ale jeśli Twój system tego wymaga, zostawiamy browser.close()
        browser.close();
        playwright.close();
    }
}

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
        // Przy połączeniu CDP pobieramy główny, aktywny kontekst przeglądarki
        BrowserContext defaultContext = browser.contexts().get(0);

        // Jeśli masz plik sesji (storageState), wgrywamy ciasteczka do istniejącego kontekstu
        if (storageState != null) {
            defaultContext.storageState(new BrowserContext.StorageStateOptions()
                    .setPath(storageState));
        }

        return defaultContext;
    }

    @Override
    public void close() {
        // Przy połączeniu przez CDP zazwyczaj nie chcemy zamykać całej przeglądarki użytkownika,
        // ale jeśli Twój system tego wymaga, zostawiamy browser.close()
        browser.close();
        playwright.close();
    }
}

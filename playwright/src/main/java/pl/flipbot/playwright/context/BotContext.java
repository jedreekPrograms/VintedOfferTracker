package pl.flipbot.playwright.context;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.Getter;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;

@Getter
public class BotContext implements AutoCloseable {

    private final BotDetailsDto bot;

    private final BrowserContext browserContext;

    private final Page page;

    public BotContext(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {
        this.bot = bot;
        this.browserContext = browserManager.createContext();
        this.page = browserContext.newPage();
    }

    @Override
    public void close() {
        browserContext.close();
    }
}
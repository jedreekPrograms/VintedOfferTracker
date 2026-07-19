package pl.flipbot.playwright.context;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.Getter;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.session.SessionManager;

import java.nio.file.Path;

@Getter
public class BotContext implements AutoCloseable {

    private final BotDetailsDto bot;

    private final BrowserContext browserContext;

    private final Page page;

    private final SessionManager sessionManager;

    public BotContext(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {
        this.bot = bot;
        this.sessionManager = new SessionManager();

        Path sessionFile = null;

        if (sessionManager.sessionExists(bot.getEmail())) {
            sessionFile = sessionManager.sessionFile(bot.getEmail());
        }

        this.browserContext = browserManager.createContext(sessionFile);

        this.page = browserContext.newPage();
    }

    public void saveSession() {

        sessionManager.saveSession(
                bot.getEmail(),
                browserContext
        );
    }

    @Override
    public void close() {
        browserContext.close();
    }
}
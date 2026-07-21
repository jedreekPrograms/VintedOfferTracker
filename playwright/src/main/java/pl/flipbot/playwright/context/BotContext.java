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

        // POPRAWKA: Pobieramy istniejącą kartę zamiast otwierać nową
        if (!browserContext.pages().isEmpty()) {
            this.page = browserContext.pages().get(0);
        } else {
            this.page = browserContext.pages().get(0); // Bezpieczny fallback, gdyby okno było puste
        }
    }

    public void saveSession() {
        sessionManager.saveSession(
                bot.getEmail(),
                browserContext
        );
    }

    @Override
    public void close() {
        // Przy CDP zazwyczaj nie zamykamy kontekstu użytkownika,
        // ale jeśli Twój przepływ tego wymaga, możesz zostawić:
        browserContext.close();
    }
}

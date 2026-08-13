package pl.flipbot.playwright.context;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.session.SessionManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
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

        if (sessionManager.sessionExists(bot.getId())) {
            sessionFile = sessionManager.sessionFile(bot.getId());
        }

        this.browserContext = browserManager.createContext(sessionFile);
        this.page = resolveMainPage();

        closeExistingExtraPages();
        registerPopupGuard();
    }


    private Page resolveMainPage() {

        List<Page> existingPages = browserContext.pages();

        for (Page existingPage : existingPages) {
            if (isVintedPage(existingPage)) {
                log.info(
                        "[BROWSER] Reusing existing Vinted page: {}",
                        existingPage.url()
                );

                return existingPage;
            }
        }

        if (!existingPages.isEmpty()) {
            Page existingPage = existingPages.getFirst();

            log.info(
                    "[BROWSER] Reusing existing browser page: {}",
                    existingPage.url()
            );

            return existingPage;
        }

        log.info(
                "[BROWSER] No existing page found. Creating new page."
        );

        return browserContext.newPage();
    }


    private void closeExistingExtraPages() {

        List<Page> pages = new ArrayList<>(browserContext.pages());

        for (Page existingPage : pages) {
            if (existingPage == page) {
                continue;
            }

            closeUnexpectedPage(
                    existingPage,
                    "existing extra page"
            );
        }
    }


    private void registerPopupGuard() {

        browserContext.onPage(
                newPage -> {
                    if (newPage == page) {
                        return;
                    }

                    handleNewPageSafely(newPage);
                }
        );

        log.info(
                "[BROWSER] Popup guard enabled for bot {}. "
                        + "Blank/transient and Vinted pages are preserved; "
                        + "only clearly external pages are closed.",
                bot.getId()
        );
    }


    private void handleNewPageSafely(Page newPage) {

        try {
            String url = normalizeUrl(newPage.url());

            /*
             * A freshly-created Playwright Page commonly starts with an empty
             * URL or about:blank before navigation is committed. Closing it at
             * this point can interrupt a legitimate Vinted interaction. This
             * was observed during brand-filter confirmation, where an empty
             * popup/page event correlated with brand persistence failures.
             *
             * Keep transient blank pages. They belong to this short-lived
             * BrowserContext and will be cleaned up when the bot job closes.
             */
            if (isTransientBlankUrl(url)) {
                log.debug(
                        "[BROWSER] Preserving transient blank popup/page for bot {}.",
                        bot.getId()
                );
                return;
            }

            if (isVintedUrl(url)) {
                log.debug(
                        "[BROWSER] Preserving additional Vinted page for bot {}. URL: {}",
                        bot.getId(),
                        url
                );
                return;
            }

            closeUnexpectedPage(
                    newPage,
                    "external popup/page"
            );

        } catch (Exception exception) {
            /*
             * Popup guarding is defensive only. It must never break the main
             * bot flow because a newly-created page was not readable yet.
             */
            log.debug(
                    "[BROWSER] Could not classify new popup/page for bot {}. "
                            + "Leaving it open until the BrowserContext closes.",
                    bot.getId(),
                    exception
            );
        }
    }


    private void closeUnexpectedPage(
            Page unexpectedPage,
            String reason
    ) {

        try {
            String url = unexpectedPage.url();

            log.warn(
                    "[BROWSER] Closing unexpected browser page. "
                            + "Bot: {}, reason: {}, URL: {}",
                    bot.getId(),
                    reason,
                    url
            );

            unexpectedPage.close();

        } catch (Exception exception) {
            log.warn(
                    "[BROWSER] Could not close unexpected page for bot {}.",
                    bot.getId(),
                    exception
            );
        }
    }


    private boolean isVintedPage(Page candidate) {

        try {
            return isVintedUrl(
                    normalizeUrl(candidate.url())
            );

        } catch (Exception exception) {
            return false;
        }
    }


    private boolean isVintedUrl(String url) {

        return url.startsWith("https://www.vinted.pl")
                || url.startsWith("https://vinted.pl");
    }


    private boolean isTransientBlankUrl(String url) {

        return url.isBlank()
                || "about:blank".equalsIgnoreCase(url);
    }


    private String normalizeUrl(String url) {

        return url == null
                ? ""
                : url.trim();
    }


    public void saveSession() {

        sessionManager.saveSession(
                bot.getId(),
                browserContext
        );
    }


    @Override
    public void close() {

        browserContext.close();
    }
}

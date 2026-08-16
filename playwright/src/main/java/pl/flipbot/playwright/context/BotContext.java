package pl.flipbot.playwright.context;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Frame;
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

    private static final int MAX_CONCURRENT_EXTRA_PAGES = 2;
    private static final int POPUP_STORM_EVENT_LIMIT = 10;

    private final BotDetailsDto bot;

    private final BrowserContext browserContext;

    private final Page page;

    private final SessionManager sessionManager;

    private int extraPageEvents;

    private boolean popupStormDetected;


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

        BrowserContext createdContext;

        try {
            createdContext = browserManager.createContext(sessionFile);
        } catch (RuntimeException exception) {
            if (sessionFile == null || !isStoredSessionRestoreFailure(exception)) {
                throw exception;
            }

            log.warn(
                    "[SESSION] Stored session for bot {} could not be restored. "
                            + "Discarding it and creating a clean browser context. reason={}",
                    bot.getId(),
                    safeMessage(exception)
            );

            sessionManager.invalidateSession(bot.getId());
            createdContext = browserManager.createContext(null);
        }

        this.browserContext = createdContext;
        this.page = resolveMainPage();

        closeExistingExtraPages();
        registerPopupGuard();
    }


    private boolean isStoredSessionRestoreFailure(
            Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {
            String message = current.getMessage();

            if (message != null) {
                String normalized = message.toLowerCase();

                if (normalized.contains("unable to restore indexeddb")
                        || normalized.contains("storagescript.restore")
                        || normalized.contains("setstoragestate")) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }


    private String safeMessage(
            Throwable throwable
    ) {
        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return throwable == null
                    ? "unknown error"
                    : throwable.getClass().getSimpleName();
        }

        return throwable.getMessage();
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

                    extraPageEvents++;

                    if (extraPageEvents > POPUP_STORM_EVENT_LIMIT) {
                        if (!popupStormDetected) {
                            popupStormDetected = true;

                            log.error(
                                    "[BROWSER] POPUP STORM detected for bot {} after {} extra-page events. "
                                            + "All subsequent extra pages will be closed immediately.",
                                    bot.getId(),
                                    extraPageEvents
                            );
                        }

                        closeUnexpectedPage(
                                newPage,
                                "popup storm hard limit"
                        );
                        return;
                    }

                    int extraPagesOpen = Math.max(
                            0,
                            browserContext.pages().size() - 1
                    );

                    if (extraPagesOpen > MAX_CONCURRENT_EXTRA_PAGES) {
                        log.warn(
                                "[BROWSER] Too many extra pages for bot {}: {} open. "
                                        + "Closing newest page defensively.",
                                bot.getId(),
                                extraPagesOpen
                        );

                        closeUnexpectedPage(
                                newPage,
                                "concurrent extra-page limit"
                        );
                        return;
                    }

                    registerNavigationGuard(newPage);
                    classifyExtraPage(newPage, "new popup/page");
                }
        );

        log.info(
                "[BROWSER] Popup guard enabled for bot {}. "
                        + "Blank pages are re-checked after navigation; "
                        + "external pages are closed; concurrent extras are limited to {}; "
                        + "popup-storm limit={} events per bot job.",
                bot.getId(),
                MAX_CONCURRENT_EXTRA_PAGES,
                POPUP_STORM_EVENT_LIMIT
        );
    }


    private void registerNavigationGuard(Page extraPage) {

        extraPage.onFrameNavigated(
                frame -> {
                    if (!isMainFrame(extraPage, frame)) {
                        return;
                    }

                    classifyExtraPage(
                            extraPage,
                            "extra page navigated"
                    );
                }
        );
    }


    private boolean isMainFrame(
            Page candidatePage,
            Frame frame
    ) {

        try {
            return frame == candidatePage.mainFrame();
        } catch (Exception exception) {
            return false;
        }
    }


    private void classifyExtraPage(
            Page extraPage,
            String reason
    ) {

        try {
            if (extraPage.isClosed()) {
                return;
            }

            String url = normalizeUrl(extraPage.url());

            if (isTransientBlankUrl(url)) {
                log.debug(
                        "[BROWSER] Waiting for transient blank extra page to navigate. Bot: {}.",
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
                    extraPage,
                    reason + ", external URL"
            );

        } catch (Exception exception) {
            log.debug(
                    "[BROWSER] Could not classify extra page for bot {}. "
                            + "The concurrent-page and popup-storm limits remain active.",
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
            if (unexpectedPage == page || unexpectedPage.isClosed()) {
                return;
            }

            String url = normalizeUrl(unexpectedPage.url());

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

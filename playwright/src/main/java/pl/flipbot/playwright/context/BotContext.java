package pl.flipbot.playwright.context;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.marketplace.MarketplaceUrls;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.session.SessionManager;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Getter
public class BotContext implements AutoCloseable {

    /*
     * Every FlipBot job is deliberately single-page. All production flows
     * (catalog filtering, item inspection, offer submission and inbox
     * negotiation) navigate the same Playwright Page. Vinted/ad-tech can still
     * try to open target=_blank windows such as adtarget.biz or
     * monetixads.com. Keeping a blank popup alive even for a fraction of a
     * second lets Chromium render a visible advertising tab before the old
     * navigation guard classifies it. Closing every additional Page at the
     * creation event prevents that redirect chain from starting at all.
     */
    private static final int EXTRA_PAGE_LOG_FIRST_EVENTS = 3;
    private static final int EXTRA_PAGE_LOG_INTERVAL = 25;

    private final BotDetailsDto bot;

    private final BrowserContext browserContext;

    private final Page page;

    private final SessionManager sessionManager;

    private final AtomicInteger extraPageEvents = new AtomicInteger();

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
        registerSinglePageGuard();
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
                    "existing extra page",
                    true
            );
        }
    }

    private void registerSinglePageGuard() {
        browserContext.onPage(
                newPage -> {
                    if (newPage == page) {
                        return;
                    }

                    int eventNumber = extraPageEvents.incrementAndGet();

                    /*
                     * Close immediately, including about:blank. Do not wait for
                     * the popup to navigate, because that is exactly the window
                     * in which the ad/RTB tab becomes visible to the user.
                     */
                    closeUnexpectedPage(
                            newPage,
                            "single-page policy, extra-page event #" + eventNumber,
                            shouldLogExtraPageEvent(eventNumber)
                    );
                }
        );

        log.info(
                "[BROWSER] Single-page guard enabled for bot {}. Every additional browser tab/window is closed immediately before an advertising or external redirect can proceed.",
                bot.getId()
        );
    }

    private boolean shouldLogExtraPageEvent(int eventNumber) {
        return eventNumber <= EXTRA_PAGE_LOG_FIRST_EVENTS
                || eventNumber % EXTRA_PAGE_LOG_INTERVAL == 0;
    }

    private void closeUnexpectedPage(
            Page unexpectedPage,
            String reason,
            boolean logEvent
    ) {
        try {
            if (unexpectedPage == page || unexpectedPage.isClosed()) {
                return;
            }

            String url = normalizeUrl(unexpectedPage.url());

            if (logEvent) {
                log.warn(
                        "[BROWSER] Closing unexpected browser page immediately. Bot: {}, reason: {}, initialURL: {}",
                        bot.getId(),
                        reason,
                        url
                );
            } else {
                log.debug(
                        "[BROWSER] Suppressed extra-page log. Bot: {}, reason: {}, initialURL: {}",
                        bot.getId(),
                        reason,
                        url
                );
            }

            unexpectedPage.close();

        } catch (Exception exception) {
            log.warn(
                    "[BROWSER] Could not close unexpected page for bot {}. reason={}",
                    bot.getId(),
                    reason,
                    exception
            );
        }
    }

    private boolean isVintedPage(Page candidate) {
        try {
            return MarketplaceUrls.isVintedUrl(
                    normalizeUrl(candidate.url())
            );
        } catch (Exception exception) {
            return false;
        }
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
        int blockedExtraPages = extraPageEvents.get();

        if (blockedExtraPages > 0) {
            log.info(
                    "[BROWSER] Bot {} job blocked {} additional browser tab/window event(s). Main page remained isolated.",
                    bot.getId(),
                    blockedExtraPages
            );
        }

        browserContext.close();
    }
}

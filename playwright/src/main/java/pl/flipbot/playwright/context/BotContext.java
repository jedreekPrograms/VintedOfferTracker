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

    private static final String ANONYMOUS_MARKET_OBSERVER_NAME =
            "Anonymous Market Observer";

    private static final int EXTRA_PAGE_LOG_FIRST_EVENTS = 3;
    private static final int EXTRA_PAGE_LOG_INTERVAL = 25;

    private final BotDetailsDto bot;

    private final BrowserContext browserContext;

    private final Page page;

    private final SessionManager sessionManager;

    private final boolean sessionRestoreEnabled;

    private final boolean sessionPersistenceEnabled;

    private final boolean storedSessionRestored;

    private final AtomicInteger extraPageEvents = new AtomicInteger();

    public BotContext(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {
        this(bot, browserManager, true, true);
    }

    public static BotContext isolated(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {
        return new BotContext(bot, browserManager, false, false);
    }

    public static BotContext readOnlySessionClone(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {
        return new BotContext(bot, browserManager, true, false);
    }

    private BotContext(
            BotDetailsDto bot,
            BrowserManager browserManager,
            boolean sessionRestoreEnabled,
            boolean sessionPersistenceEnabled
    ) {
        this.bot = bot;
        this.sessionManager = new SessionManager();
        this.sessionRestoreEnabled = sessionRestoreEnabled;
        this.sessionPersistenceEnabled = sessionPersistenceEnabled;

        Path sessionFile = null;

        if (!sessionRestoreEnabled && !sessionPersistenceEnabled) {
            log.info(
                    "[SESSION] Bot {} is using an isolated browser context. Stored production session state will not be restored or persisted by this job.",
                    bot.getId()
            );
        } else if (sessionRestoreEnabled
                && !sessionPersistenceEnabled) {
            log.info(
                    "[SESSION] Bot {} is using a read-only clone of its stored production session. The session may be restored, but this job cannot persist any browser state back to bot-{}.json.",
                    bot.getId(),
                    bot.getId()
            );
        }

        if (shouldRestoreStoredSession(bot, sessionRestoreEnabled)
                && sessionManager.sessionExists(bot.getId())) {
            sessionFile = sessionManager.sessionFile(bot.getId());
        } else if (isAnonymousMarketObserver(bot)) {
            log.info(
                    "[SESSION] Anonymous market observer {} will not restore any stored Vinted session. "
                            + "Collection stays account-free even if an old sessions/bot-{}.json file still exists locally.",
                    bot.getId(),
                    bot.getId()
            );
        }

        this.storedSessionRestored = sessionFile != null;

        /*
         * A stored production session is authoritative. BrowserManager already
         * fails closed when Playwright cannot restore storageState; do not add
         * a second fallback here that could silently turn a logged-in bot into
         * a clean context. The saved file remains untouched for diagnosis and
         * the scheduled job can retry normally.
         */
        this.browserContext = browserManager.createContext(sessionFile);

        installPreemptivePopupSuppression();
        installAnonymousObserverUiStabilityIfNeeded();

        this.page = resolveMainPage();

        closeExistingExtraPages();
        registerSinglePageGuard();
    }

    static boolean shouldRestoreStoredSession(
            BotDetailsDto bot
    ) {
        return shouldRestoreStoredSession(bot, true);
    }

    static boolean shouldRestoreStoredSession(
            BotDetailsDto bot,
            boolean sessionPersistenceEnabled
    ) {
        return sessionPersistenceEnabled && !isAnonymousMarketObserver(bot);
    }

    private static boolean isAnonymousMarketObserver(
            BotDetailsDto bot
    ) {
        if (bot == null) {
            return false;
        }

        return ANONYMOUS_MARKET_OBSERVER_NAME.equals(bot.getName())
                && isBlank(bot.getEmail())
                && isBlank(bot.getPassword());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void installPreemptivePopupSuppression() {
        browserContext.addInitScript(
                PopupSuppressionInitScript.PREEMPTIVE_POPUP_SUPPRESSION_SCRIPT
        );

        log.info(
                "[BROWSER] Preemptive popup suppression enabled for bot {}. "
                        + "window.open, links/forms targeting a new browsing context, and imperative form submissions are blocked before ad/RTB scripts can create a visible tab.",
                bot.getId()
        );
    }

    private void installAnonymousObserverUiStabilityIfNeeded() {
        if (!isAnonymousMarketObserver(bot)) {
            return;
        }

        browserContext.addInitScript(
                AnonymousObserverUiInitScript.ANONYMOUS_OBSERVER_UI_STABILITY_SCRIPT
        );

        log.info(
                "[MARKET STATS UI] Anonymous observer {} UI guard enabled. "
                        + "OneTrust accept-all consent is handled automatically and model-row labels are normalized without relaxing exact model verification.",
                bot.getId()
        );
    }

    static String preemptivePopupSuppressionScript() {
        return PopupSuppressionInitScript.PREEMPTIVE_POPUP_SUPPRESSION_SCRIPT;
    }

    static String anonymousObserverUiStabilityScript() {
        return AnonymousObserverUiInitScript.ANONYMOUS_OBSERVER_UI_STABILITY_SCRIPT;
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
        /*
         * Register both popup-specific and context-wide hooks. Chromium can
         * surface ad-tech windows through slightly different opener paths; the
         * page-level popup hook gives us the earliest owner-specific callback,
         * while BrowserContext.onPage remains the catch-all.
         */
        page.onPopup(
                popup -> handleUnexpectedPageEvent(
                        popup,
                        "main-page popup"
                )
        );

        browserContext.onPage(
                newPage -> handleUnexpectedPageEvent(
                        newPage,
                        "browser-context page"
                )
        );

        log.info(
                "[BROWSER] Single-page fail-safe enabled for bot {}. Any additional browser tab/window that still reaches Chromium will be closed immediately.",
                bot.getId()
        );
    }

    private void handleUnexpectedPageEvent(
            Page unexpectedPage,
            String source
    ) {
        try {
            if (unexpectedPage == null
                    || unexpectedPage == page
                    || unexpectedPage.isClosed()) {
                return;
            }

            int eventNumber = extraPageEvents.incrementAndGet();

            closeUnexpectedPage(
                    unexpectedPage,
                    "single-page policy, "
                            + source
                            + ", extra-page event #"
                            + eventNumber,
                    shouldLogExtraPageEvent(eventNumber)
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "[BROWSER] Failed while handling unexpected page event for bot {}. source={}",
                    bot.getId(),
                    source,
                    exception
            );
        }
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
        if (!sessionPersistenceEnabled) {
            log.debug(
                    "[SESSION] Skipping session save for non-persistent bot {} context. restoreEnabled={}.",
                    bot.getId(),
                    sessionRestoreEnabled
            );
            return;
        }

        if (isAnonymousMarketObserver(bot)) {
            log.info(
                    "[SESSION] Skipping session save for anonymous market observer {}.",
                    bot.getId()
            );
            return;
        }

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

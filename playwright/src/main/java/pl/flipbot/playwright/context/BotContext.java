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

    /*
     * FlipBot is intentionally a single-page browser application. None of the
     * supported production flows needs window.open(), target=_blank links or a
     * form that submits into a new browsing context.
     *
     * Closing an extra Playwright Page from BrowserContext.onPage() is still an
     * important fail-safe, but Chromium has already created a visible tab by
     * the time that event is emitted. Advertising/RTB code can therefore flash
     * a real landing page (for example wp.pl) for a moment before the reactive
     * guard closes it.
     *
     * This init script runs before page/iframe scripts and blocks the common
     * popup creation mechanisms at the DOM level. The existing onPage guard is
     * deliberately kept as a second line of defence for any browser mechanism
     * that bypasses the script.
     */
    private static final String PREEMPTIVE_POPUP_SUPPRESSION_SCRIPT = """
            (() => {
                const isBlankTarget = (target) =>
                    String(target ?? "").trim().toLowerCase() === "_blank";

                try {
                    window.open = () => null;
                } catch (_) {
                    // BrowserContext.onPage remains the fail-safe.
                }

                const blockBlankTargetClick = (event) => {
                    const path = typeof event.composedPath === "function"
                        ? event.composedPath()
                        : [];

                    for (const node of path) {
                        const isLink = node instanceof HTMLAnchorElement
                            || node instanceof HTMLAreaElement;

                        if (isLink && isBlankTarget(node.target)) {
                            event.preventDefault();
                            event.stopImmediatePropagation();
                            return;
                        }
                    }
                };

                document.addEventListener("click", blockBlankTargetClick, true);
                document.addEventListener("auxclick", blockBlankTargetClick, true);

                document.addEventListener("submit", (event) => {
                    const form = event.target;

                    if (form instanceof HTMLFormElement
                            && isBlankTarget(form.target)) {
                        event.preventDefault();
                        event.stopImmediatePropagation();
                    }
                }, true);
            })();
            """;

    /*
     * The anonymous market observer does not run LoginService, so it used to
     * miss LoginService.acceptCookiesIfVisible(). A late OneTrust dialog could
     * therefore cover the whole catalog and intercept clicks on category,
     * brand and model filters for 30 seconds at a time.
     *
     * The same anonymous UI also exposed a Vinted rendering variant where a
     * model row was visibly labelled (for example "Galaxy A56") but the text
     * was rendered next to the selectable test-id node instead of inside it.
     * FilterActions deliberately refuses to guess model identity, so those
     * rows failed closed even though a human could see the exact option.
     *
     * This observer-only compatibility script fixes both presentation issues:
     * - it accepts the standard OneTrust "accept all" consent as soon as it is
     *   rendered, including after navigation/reset;
     * - for a selectable Vinted model node with no readable label of its own,
     *   it copies text only from the smallest ancestor that contains exactly
     *   one brand_collection id. The text is exposed through title, which the
     *   existing strict Java matcher already reads. It never chooses a model
     *   and never fabricates a collection id.
     */
    private static final String ANONYMOUS_OBSERVER_UI_STABILITY_SCRIPT = """
            (() => {
                const MODEL_PREFIX = "selectable-item-brand_collection-";
                const MODEL_SELECTOR = `[data-testid^="${MODEL_PREFIX}"]`;
                const ACCEPT_ALL_LABELS = new Set([
                    "zgoda na wszystkie",
                    "akceptuj wszystkie",
                    "accept all",
                    "allow all"
                ]);

                const normalize = (value) =>
                    String(value ?? "")
                        .replace(/\\s+/g, " ")
                        .trim();

                const isVisible = (element) => {
                    if (!(element instanceof Element)) {
                        return false;
                    }

                    const style = window.getComputedStyle(element);
                    const rect = element.getBoundingClientRect();
                    return style.display !== "none"
                        && style.visibility !== "hidden"
                        && Number(style.opacity || "1") > 0
                        && rect.width > 0
                        && rect.height > 0;
                };

                const acceptCookieConsent = () => {
                    const primary = document.querySelector(
                        "#onetrust-accept-btn-handler"
                    );

                    if (primary instanceof HTMLElement
                            && isVisible(primary)
                            && !primary.hasAttribute("disabled")) {
                        primary.click();
                        return true;
                    }

                    const buttons = Array.from(
                        document.querySelectorAll("button")
                    );

                    for (const button of buttons) {
                        if (!isVisible(button)
                                || button.hasAttribute("disabled")) {
                            continue;
                        }

                        const label = normalize(
                            button.innerText
                                || button.textContent
                                || button.getAttribute("aria-label")
                        ).toLowerCase();

                        if (ACCEPT_ALL_LABELS.has(label)) {
                            button.click();
                            return true;
                        }
                    }

                    return false;
                };

                const collectionIdFromTestId = (testId) => {
                    const match = /^selectable-item-brand_collection-(\\d+)(?:--title)?$/
                        .exec(String(testId ?? "").trim());
                    return match ? match[1] : null;
                };

                const readableText = (element) => {
                    if (!(element instanceof Element) || !isVisible(element)) {
                        return "";
                    }

                    return normalize(
                        element.innerText
                            || element.textContent
                            || element.getAttribute("aria-label")
                            || element.getAttribute("title")
                    );
                };

                const uniqueCollectionIdsInside = (element) => {
                    const ids = new Set();

                    if (!(element instanceof Element)) {
                        return ids;
                    }

                    const ownId = collectionIdFromTestId(
                        element.getAttribute("data-testid")
                    );
                    if (ownId) {
                        ids.add(ownId);
                    }

                    for (const candidate of element.querySelectorAll(MODEL_SELECTOR)) {
                        const id = collectionIdFromTestId(
                            candidate.getAttribute("data-testid")
                        );
                        if (id) {
                            ids.add(id);
                        }
                    }

                    return ids;
                };

                const looksLikeWholeFilterPanel = (text) => {
                    const lower = normalize(text).toLowerCase();
                    return lower.includes("pokaż wyniki")
                        || lower.includes("pokaz wyniki")
                        || lower.includes("show results")
                        || lower.includes("wyczyść filtry")
                        || lower.includes("wyczysc filtry")
                        || lower.includes("clear filters");
                };

                const resolveAssociatedModelText = (candidate, collectionId) => {
                    const directTitle = document.querySelector(
                        `[data-testid="${MODEL_PREFIX}${collectionId}--title"]`
                    );
                    const directTitleText = readableText(directTitle);
                    if (directTitleText) {
                        return directTitleText;
                    }

                    let current = candidate.parentElement;

                    for (let depth = 0; current && depth < 7; depth++) {
                        const ids = uniqueCollectionIdsInside(current);

                        if (ids.size === 1 && ids.has(collectionId)) {
                            const leafTexts = [];

                            for (const leaf of current.querySelectorAll("*")) {
                                if (leaf.children.length !== 0 || !isVisible(leaf)) {
                                    continue;
                                }

                                const text = readableText(leaf);
                                if (text && !leafTexts.includes(text)) {
                                    leafTexts.push(text);
                                }
                            }

                            const joinedLeaves = normalize(leafTexts.join("\n"));
                            if (joinedLeaves
                                    && joinedLeaves.length <= 220
                                    && !looksLikeWholeFilterPanel(joinedLeaves)) {
                                return joinedLeaves;
                            }

                            const wholeText = readableText(current);
                            if (wholeText
                                    && wholeText.length <= 220
                                    && !looksLikeWholeFilterPanel(wholeText)) {
                                return wholeText;
                            }
                        }

                        current = current.parentElement;
                    }

                    return "";
                };

                const exposeModelLabels = () => {
                    const candidates = Array.from(
                        document.querySelectorAll(MODEL_SELECTOR)
                    );

                    for (const candidate of candidates) {
                        const collectionId = collectionIdFromTestId(
                            candidate.getAttribute("data-testid")
                        );
                        if (!collectionId) {
                            continue;
                        }

                        const ownReadableText = normalize(
                            candidate.innerText
                                || candidate.textContent
                                || candidate.getAttribute("aria-label")
                                || candidate.getAttribute("title")
                        );
                        if (ownReadableText) {
                            continue;
                        }

                        const associatedText = resolveAssociatedModelText(
                            candidate,
                            collectionId
                        );

                        if (associatedText) {
                            candidate.setAttribute("title", associatedText);
                        }
                    }
                };

                let scheduled = false;
                const stabilize = () => {
                    if (scheduled) {
                        return;
                    }

                    scheduled = true;
                    queueMicrotask(() => {
                        scheduled = false;
                        acceptCookieConsent();
                        exposeModelLabels();
                    });
                };

                stabilize();

                const observer = new MutationObserver(stabilize);
                observer.observe(document, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: [
                        "class",
                        "style",
                        "aria-hidden",
                        "disabled"
                    ]
                });
            })();
            """;

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

        if (shouldRestoreStoredSession(bot)
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

        installPreemptivePopupSuppression();
        installAnonymousObserverUiStabilityIfNeeded();

        this.page = resolveMainPage();

        closeExistingExtraPages();
        registerSinglePageGuard();
    }

    static boolean shouldRestoreStoredSession(
            BotDetailsDto bot
    ) {
        return !isAnonymousMarketObserver(bot);
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
                PREEMPTIVE_POPUP_SUPPRESSION_SCRIPT
        );

        log.info(
                "[BROWSER] Preemptive popup suppression enabled for bot {}. "
                        + "window.open, target=_blank links and target=_blank forms are blocked before ad/RTB scripts can create a visible tab.",
                bot.getId()
        );
    }

    private void installAnonymousObserverUiStabilityIfNeeded() {
        if (!isAnonymousMarketObserver(bot)) {
            return;
        }

        browserContext.addInitScript(
                ANONYMOUS_OBSERVER_UI_STABILITY_SCRIPT
        );

        log.info(
                "[MARKET STATS UI] Anonymous observer {} UI guard enabled. "
                        + "OneTrust accept-all consent is handled automatically and model-row labels are normalized without relaxing exact model verification.",
                bot.getId()
        );
    }

    static String preemptivePopupSuppressionScript() {
        return PREEMPTIVE_POPUP_SUPPRESSION_SCRIPT;
    }

    static String anonymousObserverUiStabilityScript() {
        return ANONYMOUS_OBSERVER_UI_STABILITY_SCRIPT;
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
                     * the popup to navigate. The preemptive DOM guard should
                     * prevent normal window.open/target=_blank popups; this
                     * handler catches anything that still reaches Chromium.
                     */
                    closeUnexpectedPage(
                            newPage,
                            "single-page policy, extra-page event #" + eventNumber,
                            shouldLogExtraPageEvent(eventNumber)
                    );
                }
        );

        log.info(
                "[BROWSER] Single-page fail-safe enabled for bot {}. Any additional browser tab/window that still reaches Chromium will be closed immediately.",
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

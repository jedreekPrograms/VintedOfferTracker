package pl.flipbot.playwright.marketplace;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

import java.util.Locale;

@Slf4j
@RequiredArgsConstructor
public class MarketplaceNavigator {

    private static final int NAVIGATION_MAX_ATTEMPTS = 3;
    private static final double NAVIGATION_RETRY_DELAY_MS = 1_000;
    private static final double NAVIGATION_TIMEOUT_MS = 30_000;
    private static final double CATALOG_SHELL_TIMEOUT_MS = 5_000;
    private static final double HOME_SHELL_TIMEOUT_MS = 10_000;
    private static final double SESSION_REFRESH_TIMEOUT_MS = 15_000;
    private static final double SESSION_REFRESH_POLL_INTERVAL_MS = 250;

    private static final String CATALOG_SEARCH_INPUT_SELECTOR =
            "form[action='/catalog'] input[name='search_text']:visible";

    private static final String HOME_LOGIN_CONTROL_SELECTOR =
            "[data-testid='header--login-button']:visible";

    private static final String HOME_AUTHENTICATED_CONTROL_SELECTOR =
            "[data-testid='header-conversations-button']:visible, "
                    + "a[href*='/inbox']:visible";

    private final BotContext context;

    public void goToHome() {
        try {
            navigate(MarketplaceUrls.HOME, true);
            waitForHomeShell();
            return;
        } catch (RuntimeException exception) {
            Page page = context.getPage();

            if (isPageClosedFailure(exception)
                    || !MarketplaceUrls.isVintedUrl(safePageUrl(page))
                    || !isRecoverableHomeSessionFailure(page, exception)) {
                throw exception;
            }

            log.warn(
                    "[SESSION REFRESH] Vinted homepage is not usable with the restored session. "
                            + "Performing one final clean-session recovery before LoginService decides whether credentials must be submitted. bot={}, currentUrl={}, reason={}",
                    context.getBot() == null ? null : context.getBot().getId(),
                    safePageUrl(page),
                    friendlyMessage(exception)
            );

            resetStoredSessionForCleanLogin(page);
            navigate(MarketplaceUrls.HOME, false);
            waitForHomeShell();
        }
    }

    public void goToCatalog() {
        navigate(MarketplaceUrls.CATALOG, false);

        if (!MarketplaceUrls.isCatalogUrl(page().url())) {
            throw new IllegalStateException(
                    "Navigation to Vinted catalog did not finish on a catalog URL. Current URL: "
                            + safePageUrl(page())
            );
        }

        waitForCatalogShell();
    }

    public void goToInbox() {
        navigate(MarketplaceUrls.INBOX, false);
    }

    public Page page() {
        return context.getPage();
    }

    private void navigate(
            String url,
            boolean allowStoredSessionRecovery
    ) {
        if (!MarketplaceUrls.isVintedUrl(url)) {
            throw new IllegalArgumentException(
                    "MarketplaceNavigator accepts only trusted Vinted URLs: "
                            + url
            );
        }

        Page page = context.getPage();
        RuntimeException lastException = null;
        boolean storedSessionRecoveryUsed = false;

        for (int attempt = 1; attempt <= NAVIGATION_MAX_ATTEMPTS; attempt++) {
            try {
                page.navigate(
                        url,
                        new Page.NavigateOptions()
                                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                                .setTimeout(NAVIGATION_TIMEOUT_MS)
                );

                if (!MarketplaceUrls.isVintedUrl(page.url())) {
                    throw new IllegalStateException(
                            "Navigation to Vinted ended on an unexpected URL: "
                                    + page.url()
                    );
                }

                waitForSessionRefreshResolution(page, url);

                if (!MarketplaceUrls.isVintedUrl(page.url())) {
                    throw new IllegalStateException(
                            "Navigation to Vinted ended on an unexpected URL after session refresh: "
                                    + page.url()
                    );
                }

                if (attempt > 1) {
                    log.info(
                            "[NAVIGATION] Vinted navigation recovered on attempt {}/{}. URL: {}",
                            attempt,
                            NAVIGATION_MAX_ATTEMPTS,
                            page.url()
                    );
                }

                return;

            } catch (RuntimeException exception) {
                lastException = exception;

                boolean sessionRefreshFailure =
                        isSessionRefreshFailure(page, exception);

                if (sessionRefreshFailure
                        && allowStoredSessionRecovery
                        && !storedSessionRecoveryUsed) {
                    storedSessionRecoveryUsed = true;
                    resetStoredSessionForCleanLogin(page);
                }

                if (isPageClosedFailure(exception)
                        || !isRetryableNavigationFailure(page, exception)
                        || attempt == NAVIGATION_MAX_ATTEMPTS) {
                    throw exception;
                }

                long delayMs =
                        (long) NAVIGATION_RETRY_DELAY_MS * attempt;

                log.warn(
                        "[NAVIGATION] Transient Vinted navigation failure on attempt {}/{}. "
                                + "Retrying in {}ms. target={}, currentUrl={}, reason={}",
                        attempt,
                        NAVIGATION_MAX_ATTEMPTS,
                        delayMs,
                        url,
                        safePageUrl(page),
                        friendlyMessage(exception)
                );

                page.waitForTimeout(delayMs);
            }
        }

        if (lastException != null) {
            throw lastException;
        }
    }

    private void waitForSessionRefreshResolution(
            Page page,
            String requestedUrl
    ) {
        if (!MarketplaceUrls.isSessionRefreshUrl(safePageUrl(page))) {
            return;
        }

        String initialRefreshUrl = safePageUrl(page);

        log.warn(
                "[SESSION REFRESH] Vinted redirected navigation through session-refresh. "
                        + "Waiting up to {}ms for it to finish. requested={}, refreshUrl={}",
                (int) SESSION_REFRESH_TIMEOUT_MS,
                requestedUrl,
                initialRefreshUrl
        );

        long deadline =
                System.currentTimeMillis()
                        + (long) SESSION_REFRESH_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            if (page.isClosed()) {
                throw new IllegalStateException(
                        "Vinted page was closed while waiting for session refresh"
                );
            }

            String currentUrl = safePageUrl(page);

            if (!MarketplaceUrls.isSessionRefreshUrl(currentUrl)) {
                log.info(
                        "[SESSION REFRESH] Vinted session refresh completed. requested={}, finalUrl={}",
                        requestedUrl,
                        currentUrl
                );
                return;
            }

            page.waitForTimeout(SESSION_REFRESH_POLL_INTERVAL_MS);
        }

        throw new IllegalStateException(
                "Vinted session refresh remained stuck for "
                        + Math.round(SESSION_REFRESH_TIMEOUT_MS)
                        + "ms. requested="
                        + requestedUrl
                        + ", currentUrl="
                        + safePageUrl(page)
        );
    }

    private void resetStoredSessionForCleanLogin(Page page) {
        Long botId = context.getBot() == null
                ? null
                : context.getBot().getId();

        log.warn(
                "[SESSION REFRESH] Stored Vinted session appears stuck during homepage navigation. "
                        + "Invalidating persisted state and clearing this browser context once so LoginService can perform a clean login. bot={}",
                botId
        );

        if (botId != null && botId > 0) {
            context.getSessionManager().invalidateSession(botId);
        }

        try {
            if (!page.isClosed() && MarketplaceUrls.isVintedUrl(page.url())) {
                page.evaluate(
                        "() => { try { localStorage.clear(); } catch (_) {} "
                                + "try { sessionStorage.clear(); } catch (_) {} }"
                );
            }
        } catch (RuntimeException exception) {
            log.debug(
                    "[SESSION REFRESH] Could not clear page storage while recovering the session: {}",
                    friendlyMessage(exception)
            );
        }

        try {
            context.getBrowserContext().clearCookies();
        } catch (RuntimeException exception) {
            log.warn(
                    "[SESSION REFRESH] Could not clear browser cookies while recovering the session: {}",
                    friendlyMessage(exception)
            );
        }
    }

    private void waitForHomeShell() {
        Page page = context.getPage();
        long deadline =
                System.currentTimeMillis()
                        + (long) HOME_SHELL_TIMEOUT_MS;

        Locator loginControl =
                page.locator(HOME_LOGIN_CONTROL_SELECTOR);
        Locator authenticatedControl =
                page.locator(HOME_AUTHENTICATED_CONTROL_SELECTOR);

        while (System.currentTimeMillis() < deadline) {
            String currentUrl = safePageUrl(page);

            if (MarketplaceUrls.isSessionRefreshUrl(currentUrl)) {
                throw new IllegalStateException(
                        "Vinted homepage returned to session-refresh while waiting for login readiness. Current URL: "
                                + currentUrl
                );
            }

            if (hasVisible(loginControl) || hasVisible(authenticatedControl)) {
                return;
            }

            page.waitForTimeout(250);
        }

        throw new IllegalStateException(
                "Vinted homepage did not expose either a login control or an authenticated inbox control within "
                        + Math.round(HOME_SHELL_TIMEOUT_MS)
                        + "ms. Refusing to infer authentication from a blank/partial page. Current URL: "
                        + safePageUrl(page)
        );
    }

    private void waitForCatalogShell() {
        Page page = context.getPage();

        try {
            Locator searchInput =
                    page.locator(CATALOG_SEARCH_INPUT_SELECTOR)
                            .first();

            searchInput.waitFor(
                    new Locator.WaitForOptions()
                            .setState(WaitForSelectorState.VISIBLE)
                            .setTimeout(CATALOG_SHELL_TIMEOUT_MS)
            );

        } catch (RuntimeException exception) {
            if (page.isClosed()) {
                throw exception;
            }

            if (MarketplaceUrls.isSessionRefreshUrl(safePageUrl(page))) {
                throw new IllegalStateException(
                        "Vinted catalog navigation fell back to a stuck session-refresh page: "
                                + safePageUrl(page),
                        exception
                );
            }

            /*
             * Some catalog jobs only need filter controls, so catalog-shell
             * readiness stays a soft guard here. SEARCH_QUERY flows will
             * still fail closed in FilterService if the input never appears.
             * The important part is avoiding the immediate post-navigation
             * race observed in the market-stats collector.
             */
            log.warn(
                    "[NAVIGATION] Vinted catalog search shell was not visible within {}ms. "
                            + "Continuing so the caller can use its own readiness checks. URL: {}",
                    (int) CATALOG_SHELL_TIMEOUT_MS,
                    safePageUrl(page)
            );
        }
    }

    private boolean isRecoverableHomeSessionFailure(
            Page page,
            Throwable throwable
    ) {
        if (isSessionRefreshFailure(page, throwable)) {
            return true;
        }

        String message = friendlyMessage(throwable)
                .toLowerCase(Locale.ROOT);

        return message.contains("homepage did not expose either a login control")
                || message.contains("homepage returned to session-refresh");
    }

    private boolean isRetryableNavigationFailure(
            Page page,
            Throwable throwable
    ) {
        String message = friendlyMessage(throwable)
                .toLowerCase(Locale.ROOT);

        if (message.contains("err_network_changed")
                || message.contains("err_name_not_resolved")
                || message.contains("err_connection_reset")
                || message.contains("err_connection_closed")
                || message.contains("err_timed_out")
                || message.contains("navigation interrupted by another one")
                || message.contains("session refresh")) {
            return true;
        }

        String currentUrl = safePageUrl(page)
                .toLowerCase(Locale.ROOT);

        return currentUrl.startsWith("chrome-error://")
                || currentUrl.startsWith("edge-error://")
                || MarketplaceUrls.isSessionRefreshUrl(currentUrl);
    }

    private boolean isSessionRefreshFailure(
            Page page,
            Throwable throwable
    ) {
        if (MarketplaceUrls.isSessionRefreshUrl(safePageUrl(page))) {
            return true;
        }

        return friendlyMessage(throwable)
                .toLowerCase(Locale.ROOT)
                .contains("session refresh");
    }

    private boolean hasVisible(Locator locator) {
        try {
            int count = locator.count();

            for (int index = 0; index < count; index++) {
                if (locator.nth(index).isVisible()) {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            return false;
        }

        return false;
    }

    private boolean isPageClosedFailure(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            String message = current.getMessage();

            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);

                if (normalized.contains("page has been closed")
                        || normalized.contains("target page, context or browser has been closed")) {
                    return true;
                }
            }

            current = current.getCause();
        }

        return false;
    }

    private String safePageUrl(Page page) {
        try {
            return page == null || page.isClosed()
                    ? "<closed>"
                    : page.url();
        } catch (RuntimeException exception) {
            return "<unavailable>";
        }
    }

    private String friendlyMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }

        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return message.lines()
                .findFirst()
                .orElse(message)
                .trim();
    }
}

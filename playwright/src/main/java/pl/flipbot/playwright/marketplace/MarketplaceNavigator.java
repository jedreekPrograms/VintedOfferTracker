package pl.flipbot.playwright.marketplace;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;

import java.util.Locale;
import java.util.function.LongSupplier;

@Slf4j
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
    private final LongSupplier clock;

    public MarketplaceNavigator(BotContext context) {
        this(context, System::currentTimeMillis);
    }

    MarketplaceNavigator(BotContext context, LongSupplier clock) {
        this.context = context;
        this.clock = clock;
    }

    public void goToHome() {
        // A slow refresh or incomplete UI does not prove that authentication
        // is invalid. Retry navigation with the same cookies/storage and let
        // the job fail without turning a transient timeout into a logout.
        navigate(MarketplaceUrls.HOME);
        waitForHomeShell();
    }

    public void goToCatalog() {
        navigate(MarketplaceUrls.CATALOG);

        if (!MarketplaceUrls.isCatalogUrl(page().url())) {
            throw new IllegalStateException(
                    "Navigation to Vinted catalog did not finish on a catalog URL. Current URL: "
                            + safePageUrl(page())
            );
        }

        waitForCatalogShell();
    }

    public void goToInbox() {
        navigate(MarketplaceUrls.INBOX);
    }

    public Page page() {
        return context.getPage();
    }

    private void navigate(String url) {
        if (!MarketplaceUrls.isVintedUrl(url)) {
            throw new IllegalArgumentException(
                    "MarketplaceNavigator accepts only trusted Vinted URLs: "
                            + url
            );
        }

        Page page = context.getPage();
        RuntimeException lastException = null;

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
                clock.getAsLong()
                        + (long) SESSION_REFRESH_TIMEOUT_MS;

        while (clock.getAsLong() < deadline) {
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

        log.warn(
                "[SESSION REFRESH] Refresh did not finish within {}ms. Keeping browser cookies, storage and the saved session unchanged; navigation may retry without a clean login reset.",
                (int) SESSION_REFRESH_TIMEOUT_MS
        );
        throw new IllegalStateException(
                "Vinted session refresh remained stuck for "
                        + Math.round(SESSION_REFRESH_TIMEOUT_MS)
                        + "ms. requested="
                        + requestedUrl
                        + ", currentUrl="
                        + safePageUrl(page)
        );
    }

    private void waitForHomeShell() {
        Page page = context.getPage();
        long deadline =
                clock.getAsLong()
                        + (long) HOME_SHELL_TIMEOUT_MS;

        Locator loginControl =
                page.locator(HOME_LOGIN_CONTROL_SELECTOR);
        Locator authenticatedControl =
                page.locator(HOME_AUTHENTICATED_CONTROL_SELECTOR);

        while (clock.getAsLong() < deadline) {
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

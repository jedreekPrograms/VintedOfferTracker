package pl.flipbot.playwright.marketplace;

import com.microsoft.playwright.Page;
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

    private final BotContext context;

    public void goToHome() {
        navigate(MarketplaceUrls.HOME);
    }

    public void goToCatalog() {
        navigate(MarketplaceUrls.CATALOG);
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
                || message.contains("navigation interrupted by another one")) {
            return true;
        }

        String currentUrl = safePageUrl(page)
                .toLowerCase(Locale.ROOT);

        return currentUrl.startsWith("chrome-error://")
                || currentUrl.startsWith("edge-error://");
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

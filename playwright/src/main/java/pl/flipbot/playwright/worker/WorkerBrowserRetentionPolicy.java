package pl.flipbot.playwright.worker;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Keeps a headless Chromium runtime warm only across immediately-ready
 * scheduled jobs.
 *
 * <p>Each scheduled job still creates its own isolated BrowserContext and
 * restores that bot's validated storage state. Reusing the outer browser
 * process therefore avoids repeated Playwright/Chromium startup cost without
 * sharing cookies, local storage or pages between bots.</p>
 *
 * <p>As soon as there is no ready work, the runtime is released. Headful
 * runtimes are never retained by generic worker slots.</p>
 */
final class WorkerBrowserRetentionPolicy {

    private WorkerBrowserRetentionPolicy() {
    }

    static boolean keepBrowserOpenBetweenJobs(boolean schedulerHeadless) {
        return schedulerHeadless;
    }

    static <T> T afterJob(
            T browserRuntime,
            boolean jobHeadless,
            boolean readyWorkAvailable,
            Consumer<T> closeAction
    ) {
        if (browserRuntime == null) {
            return null;
        }

        if (keepBrowserOpenBetweenJobs(jobHeadless)
                && readyWorkAvailable) {
            return browserRuntime;
        }

        Objects.requireNonNull(
                closeAction,
                "Browser close action is required for a scheduled runtime."
        ).accept(browserRuntime);

        return null;
    }
}

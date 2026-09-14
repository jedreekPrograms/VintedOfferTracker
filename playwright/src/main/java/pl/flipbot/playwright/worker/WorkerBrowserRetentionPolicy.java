package pl.flipbot.playwright.worker;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Decides whether a worker slot should keep its Chromium runtime alive after a job.
 *
 * <p>Headless workers keep the runtime for throughput. Headful workers deliberately
 * release it after every job so visible Chrome windows do not accumulate merely
 * because a worker slot handled work earlier in the process lifetime.</p>
 *
 * <p>Bot authentication state is not stored in the BrowserManager. Scheduled jobs
 * persist validated Playwright storage state through {@code SessionManager} before
 * the per-job BrowserContext is closed, so recycling a headful browser runtime does
 * not reset the saved bot session.</p>
 */
final class WorkerBrowserRetentionPolicy {

    private WorkerBrowserRetentionPolicy() {
    }

    static boolean keepBrowserOpenBetweenJobs(boolean schedulerHeadless) {
        return schedulerHeadless;
    }

    static <T> T afterJob(
            T browserRuntime,
            boolean schedulerHeadless,
            Consumer<T> closeAction
    ) {
        if (browserRuntime == null
                || keepBrowserOpenBetweenJobs(schedulerHeadless)) {
            return browserRuntime;
        }

        Objects.requireNonNull(
                closeAction,
                "Browser close action is required for a headful runtime."
        ).accept(browserRuntime);

        return null;
    }
}

package pl.flipbot.playwright.worker;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Decides whether a worker slot may keep its Chromium runtime after a job.
 *
 * <p>Scheduled jobs deliberately release Chromium after every completed job,
 * including in headless mode. A worker slot may stay alive and wait for later
 * work without retaining a full browser process and its renderer/GPU/utility
 * children in memory.</p>
 *
 * <p>Bot authentication state is not stored in the BrowserManager. Scheduled
 * jobs persist validated Playwright storage state through {@code SessionManager}
 * before the per-job BrowserContext is closed. A later job therefore launches
 * a fresh Chromium runtime and restores the same protected {@code bot-X.json}
 * session instead of relying on browser-process lifetime for authentication.</p>
 */
final class WorkerBrowserRetentionPolicy {

    private WorkerBrowserRetentionPolicy() {
    }

    static boolean keepBrowserOpenBetweenJobs(boolean schedulerHeadless) {
        return false;
    }

    static <T> T afterJob(
            T browserRuntime,
            boolean schedulerHeadless,
            Consumer<T> closeAction
    ) {
        if (browserRuntime == null) {
            return null;
        }

        Objects.requireNonNull(
                closeAction,
                "Browser close action is required after a scheduled job."
        ).accept(browserRuntime);

        return null;
    }
}

package pl.flipbot.playwright.worker;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Releases the Chromium runtime after each scheduled job.
 *
 * <p>Scheduled jobs persist validated authentication state through
 * {@code SessionManager} before their per-job {@code BrowserContext} is closed.
 * Keeping the entire Chromium process alive between jobs therefore does not own
 * the bot session; it only retains a large browser process tree and its caches.
 * Releasing it here keeps browser lifetime aligned with actual scheduled work.</p>
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
                "Browser close action is required for a scheduled runtime."
        ).accept(browserRuntime);

        return null;
    }
}

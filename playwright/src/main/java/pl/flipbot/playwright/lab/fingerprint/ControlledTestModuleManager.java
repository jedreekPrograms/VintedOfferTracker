package pl.flipbot.playwright.lab.fingerprint;

import lombok.extern.slf4j.Slf4j;

/**
 * Starts controlled test-only browser modules from the single
 * FlipBotPlaywrightApplication process.
 *
 * The normal marketplace workers are intentionally independent from this
 * manager. If the controlled target is missing or rejected by
 * FingerprintLabPolicy (for example a production marketplace host), the test
 * module is skipped while the rest of FlipBot continues to start normally.
 */
@Slf4j
public final class ControlledTestModuleManager {

    private static final long STOP_JOIN_TIMEOUT_MS = 5_000L;

    private Thread moduleThread;

    public synchronized void start() {
        if (moduleThread != null) {
            return;
        }

        if (!ControlledTestRuntime.isEnabled()) {
            log.info(
                    "[CONTROLLED TEST] Disabled. Normal FlipBot modules will start without the fingerprint/load/behavior fleet."
            );
            return;
        }

        try {
            ControlledTestRuntime.requireValidConfiguration();
        } catch (RuntimeException exception) {
            log.warn(
                    "[CONTROLLED TEST] BLOCKED/SKIPPED. Normal FlipBot will continue. reason={}",
                    safeMessage(exception)
            );
            return;
        }

        String targetUrl = ControlledTestRuntime.targetUrl();
        int botCount = ControlledTestRuntime.botCount();

        moduleThread = new Thread(
                () -> runControlledFleet(targetUrl, botCount),
                "flipbot-controlled-test"
        );
        moduleThread.setDaemon(true);
        moduleThread.start();

        log.info(
                "[CONTROLLED TEST] Started from FlipBotPlaywrightApplication. target={}, bots={}. Fingerprint, isolated profiles and test human-behavior modules run only inside the guarded test fleet.",
                targetUrl,
                botCount
        );
    }

    public synchronized void stop() {
        Thread current = moduleThread;
        moduleThread = null;

        if (current == null) {
            return;
        }

        current.interrupt();

        try {
            current.join(STOP_JOIN_TIMEOUT_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private void runControlledFleet(String targetUrl, int botCount) {
        try {
            FingerprintLabFleetRunner.run();
            log.info(
                    "[CONTROLLED TEST] Fleet run finished. target={}, bots={}.",
                    targetUrl,
                    botCount
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[CONTROLLED TEST] Fleet module failed without stopping normal FlipBot runtime. target={}, bots={}, reason={}",
                    targetUrl,
                    botCount,
                    safeMessage(exception),
                    exception
            );
        }
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return throwable == null
                    ? "unknown error"
                    : throwable.getClass().getSimpleName();
        }

        return throwable.getMessage()
                .lines()
                .findFirst()
                .orElse(throwable.getMessage())
                .trim();
    }
}

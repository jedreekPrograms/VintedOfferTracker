package pl.flipbot.playwright;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.lab.fingerprint.ControlledTestModuleManager;
import pl.flipbot.playwright.marketstats.MarketStatsManager;
import pl.flipbot.playwright.worker.WorkerManager;

@Slf4j
public class FlipBotPlaywrightApplication {

    public static void main(
            String[] args
    ) {

        log.info(
                "Starting FlipBot Playwright..."
        );

        WorkerManager workerManager =
                new WorkerManager();

        MarketStatsManager marketStatsManager =
                new MarketStatsManager();

        ControlledTestModuleManager controlledTestModuleManager =
                new ControlledTestModuleManager();

        Thread shutdownHook =
                new Thread(
                        () -> {
                            controlledTestModuleManager.stop();
                            marketStatsManager.stop();
                            workerManager.stop();
                        },
                        "flipbot-shutdown"
                );

        Runtime.getRuntime()
                .addShutdownHook(
                        shutdownHook
                );

        try {
            /*
             * There is one application entry point for the whole Playwright
             * process. Normal workers and market statistics always start.
             * Controlled fingerprint/load/behavior modules are started from
             * the same process when their test-only configuration is valid.
             * A blocked or invalid test target never prevents the normal
             * FlipBot runtime from starting.
             */
            controlledTestModuleManager.start();
            workerManager.start();
            marketStatsManager.start();

            Thread.currentThread()
                    .join();

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();

            log.info(
                    "FlipBot Playwright main thread was interrupted."
            );

        } finally {
            controlledTestModuleManager.stop();
            marketStatsManager.stop();
            workerManager.stop();
        }
    }
}

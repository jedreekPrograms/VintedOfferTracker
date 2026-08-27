package pl.flipbot.playwright;

import lombok.extern.slf4j.Slf4j;
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


        Thread shutdownHook =
                new Thread(
                        () -> {
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

            marketStatsManager.stop();
            workerManager.stop();
        }
    }
}

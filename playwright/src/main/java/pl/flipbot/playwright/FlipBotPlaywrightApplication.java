package pl.flipbot.playwright;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.worker.WorkerManager;

@Slf4j
public class FlipBotPlaywrightApplication {

    public static void main(String[] args) {

        log.info("Starting FlipBot Playwright...");

        try (BrowserManager browserManager = new BrowserManager()) {

            WorkerManager workerManager =
                    new WorkerManager(browserManager);

            workerManager.start();

            Thread.currentThread().join();
        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
        }

    }

}
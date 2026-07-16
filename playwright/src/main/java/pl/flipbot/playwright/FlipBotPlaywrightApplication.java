package pl.flipbot.playwright;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.worker.WorkerManager;

@Slf4j
public class FlipBotPlaywrightApplication {

    public static void main(String[] args) {

        log.info("Starting FlipBot Playwright...");

        WorkerManager workerManager =
                new WorkerManager();

        workerManager.start();

    }

}
package pl.flipbot.playwright.worker;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;

@Slf4j
@RequiredArgsConstructor
public class BotWorker implements Runnable {

    private final BotDetailsDto bot;

    private final BrowserContext context;

    private final Page page;

    public BotWorker(BotDetailsDto bot, BrowserManager browserManager) {
        this.bot = bot;

        this.context = browserManager.createContext();

        this.page = context.newPage();
    }

    @Override
    public void run() {

        log.info(
                "Worker started for bot {} ({})",
                bot.getId(),
                bot.getEmail()
        );

        while (!Thread.currentThread().isInterrupted()) {

            try {

                doWork();
                Thread.sleep(3000);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();
            } catch (Exception e) {

                log.error(
                        "Worker {} failed",
                        bot.getId(),
                        e
                );
            } finally {
                context.close();
                log.info("Worker stopped {}", bot.getId());
            }
        }

        log.info(
                "Worker stopped for bot {}",
                bot.getId()
        );
    }

    private void doWork() {

        log.info(
                "Bot {} is working...",
                bot.getId()
        );
    }
}

package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;

@Slf4j
public class BotWorkerRuntime implements Runnable {

    private final BotDetailsDto bot;


    public BotWorkerRuntime(
            BotDetailsDto bot
    ) {

        if (
                bot == null
                        || bot.getId() == null
                        || bot.getId() <= 0
        ) {

            throw new IllegalArgumentException(
                    "Bot details with a positive ID are required."
            );
        }


        this.bot =
                bot;
    }


    @Override
    public void run() {

        Long botId =
                bot.getId();


        log.info(
                "[RUNTIME] Creating isolated Playwright runtime for bot {} on thread {}.",
                botId,
                Thread.currentThread().getName()
        );


        try (
                BrowserManager browserManager =
                        new BrowserManager()
        ) {

            BotWorker worker =
                    new BotWorker(
                            bot,
                            browserManager
                    );


            worker.run();

        } catch (Exception exception) {

            log.error(
                    "[RUNTIME] Isolated Playwright runtime for bot {} failed.",
                    botId,
                    exception
            );

        } finally {

            log.info(
                    "[RUNTIME] Isolated Playwright runtime for bot {} finished on thread {}.",
                    botId,
                    Thread.currentThread().getName()
            );
        }
    }
}

package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class BotWorkerRuntime implements Runnable {

    private final BotDetailsDto bot;

    private final AtomicBoolean started =
            new AtomicBoolean(false);

    private final AtomicBoolean finished =
            new AtomicBoolean(false);


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


    public boolean isStarted() {

        return started.get();
    }


    public boolean isFinished() {

        return finished.get();
    }


    @Override
    public void run() {

        if (
                !started.compareAndSet(
                        false,
                        true
                )
        ) {

            throw new IllegalStateException(
                    "BotWorkerRuntime can only be executed once."
            );
        }


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

            finished.set(
                    true
            );


            log.info(
                    "[RUNTIME] Isolated Playwright runtime for bot {} finished on thread {}.",
                    botId,
                    Thread.currentThread().getName()
            );
        }
    }
}

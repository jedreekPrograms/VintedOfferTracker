package pl.flipbot.playwright.worker;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.model.BotDetailsDto;

@Slf4j
@RequiredArgsConstructor
public class BotWorker implements Runnable {

    private final BotDetailsDto bot;

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

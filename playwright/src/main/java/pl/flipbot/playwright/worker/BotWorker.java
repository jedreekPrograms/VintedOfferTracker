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
    }
}

package pl.flipbot.playwright;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.RunningBotDto;
import pl.flipbot.playwright.worker.BotWorker;

import java.util.List;

@Slf4j
public class TestApplication {

    public static void main(String[] args) {

        BotApiClient botApiClient =
                new BotApiClient();

        List<RunningBotDto> runningBots =
                botApiClient.getRunningBots();

        if (runningBots.isEmpty()) {

            throw new IllegalStateException(
                    "No running bots found in backend. "
                            + "Create a bot and start it first."
            );
        }

        RunningBotDto runningBot =
                runningBots.getFirst();

        Long botId =
                runningBot.getId();

        log.info(
                "Loading running bot {} from backend",
                botId
        );

        BotDetailsDto bot =
                botApiClient.getBot(
                        botId
                );

        try (BrowserManager browserManager =
                     new BrowserManager()) {

            BotWorker worker =
                    new BotWorker(
                            bot,
                            browserManager
                    );

            worker.run();
        }
    }
}
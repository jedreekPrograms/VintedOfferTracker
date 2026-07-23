package pl.flipbot.playwright.TestApplication;

import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.worker.BotWorker;

public class TestApplication {

    public static void main(String[] args) {

        BotDetailsDto bot = new BotDetailsDto();

        bot.setId(1L);
        bot.setEmail("kamil548328@gmail.com");
        bot.setPassword("Lenovok6notexd@");

        bot.setConfiguration(new BotConfigurationDto());

        try (BrowserManager browserManager = new BrowserManager()) {

            BotWorker worker =
                    new BotWorker(bot, browserManager);

            worker.run();

        }

    }

}
package pl.flipbot.playwright.testdata;

import pl.flipbot.playwright.model.BotDetailsDto;

public final class TestBotFactory {

    private TestBotFactory() {
    }

    public static BotDetailsDto configure(BotDetailsDto bot) {

        bot.setConfiguration(
                TestBotConfigurationFactory.create()
        );

        return bot;

    }

}
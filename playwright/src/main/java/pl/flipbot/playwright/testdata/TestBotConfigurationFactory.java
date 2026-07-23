package pl.flipbot.playwright.testdata;

import pl.flipbot.playwright.model.BotConfigurationDto;

import java.math.BigDecimal;
import java.util.List;

public final class TestBotConfigurationFactory {

    private TestBotConfigurationFactory() {
    }

    public static BotConfigurationDto create() {

        BotConfigurationDto configuration = new BotConfigurationDto();

        configuration.setMarketplace("VINTED");

        configuration.setCategoryPath(List.of(
                "Elektronika",
                "Telefony komórkowe i komunikacja",
                "Telefony komórkowe"
        ));

        configuration.setBrand("Samsung");

        configuration.setModel("Galaxy S25");

        configuration.setMinPrice(
                BigDecimal.valueOf(1000)
        );

        configuration.setMaxPrice(
                BigDecimal.valueOf(2500)
        );

        configuration.setDailyNegotiationBudget(25);

        return configuration;

    }

}
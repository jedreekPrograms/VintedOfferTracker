package pl.flipbot.marketstats;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.dictionary.DictionaryBrand;
import pl.flipbot.dictionary.DictionaryCategory;
import pl.flipbot.dictionary.DictionaryModel;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MarketStatsTargetOrderingTest {

    @Test
    void prioritizesExactActiveModelsThenTheirMarketSegment() {
        DictionaryBrand samsung = brand(1L, "Samsung");
        DictionaryBrand apple = brand(2L, "Apple");

        DictionaryCategory phones = category(
                1L,
                "Telefony komórkowe",
                "Elektronika > Telefony komórkowe i komunikacja > Telefony komórkowe"
        );
        DictionaryCategory tablets = category(
                2L,
                "Tablety",
                "Elektronika > Tablety, czytniki e-booków i akcesoria > Tablety"
        );

        DictionaryModel s25 = model(
                10L,
                samsung,
                phones,
                "Galaxy S25"
        );
        DictionaryModel s25Ultra = model(
                11L,
                samsung,
                phones,
                "Galaxy S25 Ultra"
        );
        DictionaryModel ipad = model(
                12L,
                apple,
                tablets,
                "iPad Air 11 (2024)"
        );

        BotConfiguration runningS25Bot = BotConfiguration.builder()
                .bot(
                        Bot.builder()
                                .id(100L)
                                .status(BotStatus.RUNNING)
                                .marketStatsObserver(false)
                                .build()
                )
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("Galaxy S25")
                .categoryPath(
                        List.of(
                                "Elektronika",
                                "Telefony komórkowe i komunikacja",
                                "Telefony komórkowe"
                        )
                )
                .build();

        List<DictionaryModel> models =
                new ArrayList<>(List.of(ipad, s25Ultra, s25));

        models.sort(
                MarketStatsTargetOrdering.comparator(
                        List.of(runningS25Bot)
                )
        );

        assertEquals(
                List.of(s25, s25Ultra, ipad),
                models
        );
    }

    @Test
    void stoppedAndObserverBotsDoNotChangePriority() {
        DictionaryBrand samsung = brand(1L, "Samsung");
        DictionaryCategory phones = category(
                1L,
                "Telefony",
                "Elektronika > Telefony"
        );
        DictionaryModel s25 = model(
                10L,
                samsung,
                phones,
                "Galaxy S25"
        );

        BotConfiguration stopped = configuration(
                BotStatus.STOPPED,
                false
        );
        BotConfiguration observer = configuration(
                BotStatus.RUNNING,
                true
        );

        assertEquals(
                3,
                MarketStatsTargetOrdering.priority(
                        s25,
                        List.of(stopped, observer)
                )
        );
    }

    private BotConfiguration configuration(
            BotStatus status,
            boolean observer
    ) {
        return BotConfiguration.builder()
                .bot(
                        Bot.builder()
                                .status(status)
                                .marketStatsObserver(observer)
                                .build()
                )
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("Galaxy S25")
                .categoryPath(
                        List.of(
                                "Elektronika",
                                "Telefony"
                        )
                )
                .build();
    }

    private DictionaryBrand brand(
            Long id,
            String name
    ) {
        return DictionaryBrand.builder()
                .id(id)
                .name(name)
                .build();
    }

    private DictionaryCategory category(
            Long id,
            String name,
            String path
    ) {
        return DictionaryCategory.builder()
                .id(id)
                .name(name)
                .path(path)
                .build();
    }

    private DictionaryModel model(
            Long id,
            DictionaryBrand brand,
            DictionaryCategory category,
            String name
    ) {
        return DictionaryModel.builder()
                .id(id)
                .brand(brand)
                .category(category)
                .targetMode(TargetMode.VINTED_MODEL)
                .name(name)
                .build();
    }
}

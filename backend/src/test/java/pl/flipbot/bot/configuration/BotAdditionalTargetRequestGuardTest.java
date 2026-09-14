package pl.flipbot.bot.configuration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.dto.UpsertBotAdditionalTargetRequest;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotAdditionalTargetRequestGuardTest {

    private static final long BOT_ID = 7L;

    private BotRepository botRepository;
    private BotAdditionalTargetRepository additionalTargetRepository;
    private BotTargetDictionaryCompatibilityGuard dictionaryCompatibilityGuard;
    private BotAdditionalTargetRequestGuard guard;
    private BotConfiguration main;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        additionalTargetRepository = mock(BotAdditionalTargetRepository.class);
        dictionaryCompatibilityGuard = mock(BotTargetDictionaryCompatibilityGuard.class);
        guard = new BotAdditionalTargetRequestGuard(
                botRepository,
                additionalTargetRepository,
                dictionaryCompatibilityGuard
        );

        main = BotConfiguration.builder()
                .categoryPath(List.of("Elektronika", "Telefony"))
                .brand("Apple")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("iPhone 13")
                .minPrice(new BigDecimal("800"))
                .maxPrice(new BigDecimal("1800"))
                .dailyNegotiationBudget(5)
                .negotiationSteps(new ArrayList<>())
                .build();

        Bot bot = Bot.builder()
                .id(BOT_ID)
                .configuration(main)
                .build();
        main.setBot(bot);

        when(botRepository.findById(BOT_ID)).thenReturn(Optional.of(bot));
        when(additionalTargetRepository
                .findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(BOT_ID))
                .thenReturn(List.of());
    }

    @Test
    void rejectsDuplicateOfMainProductIgnoringWhitespaceAndCase() {
        UpsertBotAdditionalTargetRequest request = request(
                List.of("Elektronika", "Telefony"),
                " apple ",
                TargetMode.VINTED_MODEL,
                "  IPHONE   13 ",
                null,
                3
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> guard.validateCreate(BOT_ID, request)
        );

        assertTrue(exception.getMessage().contains("produkt główny"));
    }

    @Test
    void rejectsDuplicateOfAnotherActiveAdditionalProduct() {
        BotAdditionalTarget existing = BotAdditionalTarget.builder()
                .id(44L)
                .configuration(main)
                .active(true)
                .categoryPath(new ArrayList<>(List.of("Elektronika", "Tablety")))
                .brand("Samsung")
                .targetMode(TargetMode.SEARCH_QUERY)
                .searchQuery("Galaxy Tab S10 Ultra")
                .build();

        when(additionalTargetRepository
                .findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(BOT_ID))
                .thenReturn(List.of(existing));

        UpsertBotAdditionalTargetRequest request = request(
                List.of("Elektronika", "Tablety"),
                "SAMSUNG",
                TargetMode.SEARCH_QUERY,
                null,
                " galaxy   tab s10 ultra ",
                2
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> guard.validateCreate(BOT_ID, request)
        );

        assertTrue(exception.getMessage().contains("dodatkowy produkt"));
    }

    @Test
    void updateIgnoresTheTargetBeingEdited() {
        BotAdditionalTarget existing = BotAdditionalTarget.builder()
                .id(44L)
                .configuration(main)
                .active(true)
                .categoryPath(new ArrayList<>(List.of("Elektronika", "Tablety")))
                .brand("Samsung")
                .targetMode(TargetMode.SEARCH_QUERY)
                .searchQuery("Galaxy Tab S10 Ultra")
                .build();

        when(additionalTargetRepository
                .findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(BOT_ID))
                .thenReturn(List.of(existing));

        UpsertBotAdditionalTargetRequest request = request(
                List.of("Elektronika", "Tablety"),
                "Samsung",
                TargetMode.SEARCH_QUERY,
                null,
                "Galaxy Tab S10 Ultra",
                2
        );

        assertDoesNotThrow(
                () -> guard.validateUpdate(BOT_ID, 44L, request)
        );
    }

    @Test
    void rejectsLadderLongerThanSharedDailyBudget() {
        main.setDailyNegotiationBudget(3);

        UpsertBotAdditionalTargetRequest request = request(
                List.of("Elektronika", "Tablety"),
                "Samsung",
                TargetMode.VINTED_MODEL,
                "Galaxy Tab S10",
                null,
                4
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> guard.validateCreate(BOT_ID, request)
        );

        assertTrue(exception.getMessage().contains("wspólnego dziennego budżetu"));
    }

    @Test
    void acceptsDistinctProductWithinSharedBudget() {
        UpsertBotAdditionalTargetRequest request = request(
                List.of("Elektronika", "Tablety"),
                "Samsung",
                TargetMode.VINTED_MODEL,
                "Galaxy Tab S10",
                null,
                5
        );

        assertDoesNotThrow(() -> guard.validateCreate(BOT_ID, request));
    }

    private UpsertBotAdditionalTargetRequest request(
            List<String> categoryPath,
            String brand,
            TargetMode targetMode,
            String model,
            String searchQuery,
            int stepCount
    ) {
        UpsertBotAdditionalTargetRequest request =
                new UpsertBotAdditionalTargetRequest();
        request.setCategoryPath(categoryPath);
        request.setBrand(brand);
        request.setTargetMode(targetMode);
        request.setModel(model);
        request.setSearchQuery(searchQuery);
        request.setMinPrice(new BigDecimal("100"));
        request.setMaxPrice(new BigDecimal("1000"));
        request.setAutoRaiseOfferToVintedMinimum(false);

        List<CreateNegotiationStepRequest> steps = new ArrayList<>();
        for (int index = 1; index <= stepCount; index++) {
            CreateNegotiationStepRequest step = new CreateNegotiationStepRequest();
            step.setOfferPrice(BigDecimal.valueOf(100L + index));
            step.setMaxAcceptedCounterOffer(BigDecimal.valueOf(150L + index));
            step.setMessage("Krok " + index);
            steps.add(step);
        }
        request.setNegotiationSteps(steps);
        return request;
    }
}

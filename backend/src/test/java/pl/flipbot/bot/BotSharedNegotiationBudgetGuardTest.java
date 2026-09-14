package pl.flipbot.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.bot.dto.CreateBotConfigurationRequest;
import pl.flipbot.bot.dto.CreateBotRequest;
import pl.flipbot.bot.dto.UpdateBotRequest;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotSharedNegotiationBudgetGuardTest {

    private static final long BOT_ID = 12L;

    private BotRepository botRepository;
    private BotAdditionalTargetRepository additionalTargetRepository;
    private ListingRepository listingRepository;
    private BotSharedNegotiationBudgetGuard guard;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        additionalTargetRepository = mock(BotAdditionalTargetRepository.class);
        listingRepository = mock(ListingRepository.class);
        guard = new BotSharedNegotiationBudgetGuard(
                botRepository,
                additionalTargetRepository,
                listingRepository
        );

        when(botRepository.findById(BOT_ID)).thenReturn(
                Optional.of(Bot.builder().id(BOT_ID).build())
        );
        when(additionalTargetRepository
                .findAllByConfigurationBotIdOrderByIdAsc(BOT_ID))
                .thenReturn(List.of());
        when(listingRepository
                .findDistinctAdditionalTargetIdsByBotIdAndStatusIn(
                        eq(BOT_ID),
                        eq(Set.of(ListingStatus.NEGOTIATING))
                ))
                .thenReturn(List.of());
    }

    @Test
    void createRejectsMainLadderLongerThanDailyBudget() {
        CreateBotRequest request = new CreateBotRequest();
        request.setConfiguration(configuration(2, 3));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> guard.validateCreate(request)
        );

        assertTrue(exception.getMessage().contains("produktu głównego"));
    }

    @Test
    void updateRejectsBudgetLowerThanActiveAdditionalProductLadder() {
        BotAdditionalTarget target = target(55L, true, 4);

        when(additionalTargetRepository
                .findAllByConfigurationBotIdOrderByIdAsc(BOT_ID))
                .thenReturn(List.of(target));

        UpdateBotRequest request = new UpdateBotRequest();
        request.setConfiguration(configuration(3, 2));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> guard.validateUpdate(BOT_ID, request)
        );

        assertTrue(exception.getMessage().contains("dodatkowego produktu 55"));
    }

    @Test
    void updateRejectsBudgetLowerThanDisabledProductWithRunningNegotiation() {
        BotAdditionalTarget target = target(56L, false, 4);

        when(additionalTargetRepository
                .findAllByConfigurationBotIdOrderByIdAsc(BOT_ID))
                .thenReturn(List.of(target));
        when(listingRepository
                .findDistinctAdditionalTargetIdsByBotIdAndStatusIn(
                        eq(BOT_ID),
                        eq(Set.of(ListingStatus.NEGOTIATING))
                ))
                .thenReturn(List.of(56L));

        UpdateBotRequest request = new UpdateBotRequest();
        request.setConfiguration(configuration(3, 2));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> guard.validateUpdate(BOT_ID, request)
        );

        assertTrue(exception.getMessage().contains("wyłączonego z trwającą negocjacją"));
        assertTrue(exception.getMessage().contains("56"));
    }

    @Test
    void updateIgnoresDisabledHistoricalProductWithoutRunningNegotiation() {
        BotAdditionalTarget target = target(57L, false, 5);

        when(additionalTargetRepository
                .findAllByConfigurationBotIdOrderByIdAsc(BOT_ID))
                .thenReturn(List.of(target));

        UpdateBotRequest request = new UpdateBotRequest();
        request.setConfiguration(configuration(3, 2));

        assertDoesNotThrow(() -> guard.validateUpdate(BOT_ID, request));
    }

    @Test
    void updateAllowsBudgetEqualToLongestActiveProductLadder() {
        BotAdditionalTarget target = target(55L, true, 4);
        target.setCategoryPath(new ArrayList<>(List.of("Elektronika", "Tablety")));
        target.setBrand("Samsung");
        target.setTargetMode(TargetMode.VINTED_MODEL);
        target.setModel("Galaxy Tab S10");

        when(additionalTargetRepository
                .findAllByConfigurationBotIdOrderByIdAsc(BOT_ID))
                .thenReturn(List.of(target));

        UpdateBotRequest request = new UpdateBotRequest();
        CreateBotConfigurationRequest configuration = configuration(4, 4);
        configuration.setCategoryPath(List.of("Elektronika", "Telefony"));
        configuration.setBrand("Apple");
        configuration.setTargetMode(TargetMode.VINTED_MODEL);
        configuration.setModel("iPhone 13");
        request.setConfiguration(configuration);

        assertDoesNotThrow(() -> guard.validateUpdate(BOT_ID, request));
    }

    @Test
    void updateRejectsMainProductDuplicatingActiveAdditionalTarget() {
        BotAdditionalTarget target = target(55L, true, 1);
        target.setCategoryPath(new ArrayList<>(List.of("Elektronika", "Tablety")));
        target.setBrand("Samsung");
        target.setTargetMode(TargetMode.SEARCH_QUERY);
        target.setSearchQuery("Galaxy Tab S10 Ultra");

        when(additionalTargetRepository
                .findAllByConfigurationBotIdOrderByIdAsc(BOT_ID))
                .thenReturn(List.of(target));

        UpdateBotRequest request = new UpdateBotRequest();
        CreateBotConfigurationRequest configuration = configuration(5, 2);
        configuration.setCategoryPath(List.of("Elektronika", "Tablety"));
        configuration.setBrand(" samsung ");
        configuration.setTargetMode(TargetMode.SEARCH_QUERY);
        configuration.setSearchQuery("  GALAXY   TAB S10 ultra ");
        request.setConfiguration(configuration);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> guard.validateUpdate(BOT_ID, request)
        );

        assertTrue(exception.getMessage().contains("tego samego celu"));
        assertTrue(exception.getMessage().contains("55"));
    }

    private BotAdditionalTarget target(Long id, boolean active, int stepCount) {
        List<NegotiationStep> steps = new ArrayList<>();
        for (int index = 0; index < stepCount; index++) {
            steps.add(NegotiationStep.builder().stepNumber(index + 1).build());
        }
        return BotAdditionalTarget.builder()
                .id(id)
                .active(active)
                .negotiationSteps(steps)
                .build();
    }

    private CreateBotConfigurationRequest configuration(
            int budget,
            int mainStepCount
    ) {
        CreateBotConfigurationRequest configuration =
                new CreateBotConfigurationRequest();
        configuration.setDailyNegotiationBudget(budget);

        List<CreateNegotiationStepRequest> steps = new ArrayList<>();
        for (int index = 0; index < mainStepCount; index++) {
            steps.add(new CreateNegotiationStepRequest());
        }
        configuration.setNegotiationSteps(steps);
        return configuration;
    }
}

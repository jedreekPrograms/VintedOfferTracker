package pl.flipbot.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.dto.CreateBotConfigurationRequest;
import pl.flipbot.bot.dto.CreateBotRequest;
import pl.flipbot.bot.dto.UpdateBotRequest;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotSharedNegotiationBudgetGuardTest {

    private static final long BOT_ID = 12L;

    private BotRepository botRepository;
    private BotAdditionalTargetRepository additionalTargetRepository;
    private BotSharedNegotiationBudgetGuard guard;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        additionalTargetRepository = mock(BotAdditionalTargetRepository.class);
        guard = new BotSharedNegotiationBudgetGuard(
                botRepository,
                additionalTargetRepository
        );

        when(botRepository.findById(BOT_ID)).thenReturn(
                Optional.of(Bot.builder().id(BOT_ID).build())
        );
        when(additionalTargetRepository
                .findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(BOT_ID))
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
        BotAdditionalTarget target = BotAdditionalTarget.builder()
                .id(55L)
                .active(true)
                .negotiationSteps(new ArrayList<>(List.of(
                        NegotiationStep.builder().stepNumber(1).build(),
                        NegotiationStep.builder().stepNumber(2).build(),
                        NegotiationStep.builder().stepNumber(3).build(),
                        NegotiationStep.builder().stepNumber(4).build()
                )))
                .build();

        when(additionalTargetRepository
                .findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(BOT_ID))
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
    void updateAllowsBudgetEqualToLongestActiveProductLadder() {
        BotAdditionalTarget target = BotAdditionalTarget.builder()
                .id(55L)
                .active(true)
                .negotiationSteps(new ArrayList<>(List.of(
                        NegotiationStep.builder().stepNumber(1).build(),
                        NegotiationStep.builder().stepNumber(2).build(),
                        NegotiationStep.builder().stepNumber(3).build(),
                        NegotiationStep.builder().stepNumber(4).build()
                )))
                .build();

        when(additionalTargetRepository
                .findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(BOT_ID))
                .thenReturn(List.of(target));

        UpdateBotRequest request = new UpdateBotRequest();
        request.setConfiguration(configuration(4, 4));

        assertDoesNotThrow(() -> guard.validateUpdate(BOT_ID, request));
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

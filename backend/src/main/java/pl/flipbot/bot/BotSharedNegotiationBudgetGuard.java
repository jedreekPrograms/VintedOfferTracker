package pl.flipbot.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.dto.CreateBotConfigurationRequest;
import pl.flipbot.bot.dto.CreateBotRequest;
import pl.flipbot.bot.dto.UpdateBotRequest;
import pl.flipbot.exception.BotNotFoundException;

import java.util.List;

/**
 * Keeps the single account-wide daily action budget compatible with every
 * product ladder attached to the bot.
 */
@Component
@RequiredArgsConstructor
public class BotSharedNegotiationBudgetGuard {

    private final BotRepository botRepository;
    private final BotAdditionalTargetRepository additionalTargetRepository;

    public void validateCreate(CreateBotRequest request) {
        if (request == null) {
            return;
        }
        validateMainLadder(request.getConfiguration());
    }

    public void validateUpdate(Long botId, UpdateBotRequest request) {
        if (request == null) {
            return;
        }

        CreateBotConfigurationRequest configuration = request.getConfiguration();
        validateMainLadder(configuration);

        if (configuration == null
                || configuration.getDailyNegotiationBudget() == null) {
            return;
        }

        botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException(botId));

        int requestedBudget = configuration.getDailyNegotiationBudget();
        List<BotAdditionalTarget> activeTargets = additionalTargetRepository
                .findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(botId);

        for (BotAdditionalTarget target : activeTargets) {
            if (target == null || target.getNegotiationSteps() == null) {
                continue;
            }

            int stepCount = target.getNegotiationSteps().size();
            if (stepCount > requestedBudget) {
                throw new IllegalArgumentException(
                        "Dzienny budżet negocjacyjny nie może być niższy niż liczba kroków aktywnego dodatkowego produktu "
                                + target.getId()
                                + " ("
                                + stepCount
                                + ")."
                );
            }
        }
    }

    private void validateMainLadder(CreateBotConfigurationRequest configuration) {
        if (configuration == null
                || configuration.getDailyNegotiationBudget() == null
                || configuration.getNegotiationSteps() == null) {
            return;
        }

        int budget = configuration.getDailyNegotiationBudget();
        int stepCount = configuration.getNegotiationSteps().size();
        if (budget > 0 && stepCount > budget) {
            throw new IllegalArgumentException(
                    "Liczba kroków produktu głównego nie może przekraczać dziennego budżetu negocjacyjnego ("
                            + budget
                            + ")."
            );
        }
    }
}

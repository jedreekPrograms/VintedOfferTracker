package pl.flipbot.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.bot.dto.CreateBotConfigurationRequest;
import pl.flipbot.bot.dto.CreateBotRequest;
import pl.flipbot.bot.dto.UpdateBotRequest;
import pl.flipbot.exception.BotNotFoundException;

import java.util.List;

/**
 * Keeps the single account-wide daily action budget compatible with every
 * product ladder attached to the bot and prevents the main product from being
 * edited into the same target identity as an active additional product.
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

        if (configuration == null) {
            return;
        }

        botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException(botId));

        List<BotAdditionalTarget> activeTargets = additionalTargetRepository
                .findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(botId);

        validateSharedBudget(configuration, activeTargets);
        validateMainTargetIdentity(configuration, activeTargets);
    }

    private void validateSharedBudget(
            CreateBotConfigurationRequest configuration,
            List<BotAdditionalTarget> activeTargets
    ) {
        Integer requestedBudget = configuration.getDailyNegotiationBudget();
        if (requestedBudget == null) {
            return;
        }

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

    private void validateMainTargetIdentity(
            CreateBotConfigurationRequest configuration,
            List<BotAdditionalTarget> activeTargets
    ) {
        for (BotAdditionalTarget target : activeTargets) {
            if (target != null && sameTarget(configuration, target)) {
                throw new IllegalArgumentException(
                        "Produkt główny nie może mieć tego samego celu co aktywny dodatkowy produkt "
                                + target.getId()
                                + "."
                );
            }
        }
    }

    private boolean sameTarget(
            CreateBotConfigurationRequest main,
            BotAdditionalTarget additional
    ) {
        TargetMode mainMode = main.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : main.getTargetMode();

        if (!normalizeCategoryPath(main.getCategoryPath()).equals(
                normalizeCategoryPath(additional.getCategoryPath())
        )
                || !sameNormalizedText(main.getBrand(), additional.getBrand())
                || mainMode != additional.getTargetMode()) {
            return false;
        }

        if (mainMode == TargetMode.VINTED_MODEL) {
            return sameNormalizedText(main.getModel(), additional.getModel());
        }
        if (mainMode == TargetMode.SEARCH_QUERY) {
            return sameNormalizedText(
                    main.getSearchQuery(),
                    additional.getSearchQuery()
            );
        }
        return false;
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

    private List<String> normalizeCategoryPath(List<String> path) {
        if (path == null) {
            return List.of();
        }
        return path.stream()
                .map(this::normalizeText)
                .toList();
    }

    private boolean sameNormalizedText(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        return normalizeText(left).equalsIgnoreCase(normalizeText(right));
    }

    private String normalizeText(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }
}

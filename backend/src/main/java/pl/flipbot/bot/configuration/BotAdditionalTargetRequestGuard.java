package pl.flipbot.bot.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.dto.UpsertBotAdditionalTargetRequest;
import pl.flipbot.exception.BotNotFoundException;

import java.util.List;

/**
 * Cross-product validation that cannot be expressed by Bean Validation on the
 * request alone because it depends on the bot's main product and its other
 * active additional products.
 */
@Component
@RequiredArgsConstructor
public class BotAdditionalTargetRequestGuard {

    private final BotRepository botRepository;
    private final BotAdditionalTargetRepository additionalTargetRepository;

    public void validateCreate(
            Long botId,
            UpsertBotAdditionalTargetRequest request
    ) {
        validate(botId, null, request);
    }

    public void validateUpdate(
            Long botId,
            Long targetId,
            UpsertBotAdditionalTargetRequest request
    ) {
        validate(botId, targetId, request);
    }

    private void validate(
            Long botId,
            Long targetId,
            UpsertBotAdditionalTargetRequest request
    ) {
        Bot bot = botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException(botId));
        BotConfiguration main = bot.getConfiguration();

        if (main == null) {
            throw new IllegalStateException(
                    "Bot nie ma konfiguracji głównego produktu."
            );
        }

        validateSharedDailyBudget(main, request);

        if (sameTarget(main, request)) {
            throw new IllegalArgumentException(
                    "Ten produkt jest już skonfigurowany jako produkt główny bota."
            );
        }

        for (BotAdditionalTarget existing : additionalTargetRepository
                .findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(botId)) {
            if (existing == null
                    || (targetId != null && targetId.equals(existing.getId()))) {
                continue;
            }

            if (sameTarget(existing, request)) {
                throw new IllegalArgumentException(
                        "Ten produkt jest już skonfigurowany jako dodatkowy produkt tego bota."
                );
            }
        }
    }

    private void validateSharedDailyBudget(
            BotConfiguration main,
            UpsertBotAdditionalTargetRequest request
    ) {
        Integer dailyBudget = main.getDailyNegotiationBudget();
        if (dailyBudget == null || dailyBudget <= 0) {
            throw new IllegalStateException(
                    "Bot nie ma prawidłowego wspólnego dziennego budżetu negocjacyjnego."
            );
        }

        if (request != null
                && request.getNegotiationSteps() != null
                && request.getNegotiationSteps().size() > dailyBudget) {
            throw new IllegalArgumentException(
                    "Liczba kroków dodatkowego produktu nie może przekraczać wspólnego dziennego budżetu bota ("
                            + dailyBudget
                            + ")."
            );
        }
    }

    private boolean sameTarget(
            BotConfiguration existing,
            UpsertBotAdditionalTargetRequest request
    ) {
        if (existing == null || request == null) {
            return false;
        }

        return sameIdentity(
                existing.getCategoryPath(),
                existing.getBrand(),
                existing.getTargetMode(),
                existing.getModel(),
                existing.getSearchQuery(),
                request
        );
    }

    private boolean sameTarget(
            BotAdditionalTarget existing,
            UpsertBotAdditionalTargetRequest request
    ) {
        if (existing == null || request == null) {
            return false;
        }

        return sameIdentity(
                existing.getCategoryPath(),
                existing.getBrand(),
                existing.getTargetMode(),
                existing.getModel(),
                existing.getSearchQuery(),
                request
        );
    }

    private boolean sameIdentity(
            List<String> categoryPath,
            String brand,
            TargetMode targetMode,
            String model,
            String searchQuery,
            UpsertBotAdditionalTargetRequest request
    ) {
        if (!normalizeCategoryPath(categoryPath).equals(
                normalizeCategoryPath(request.getCategoryPath())
        )
                || !sameNormalizedText(brand, request.getBrand())
                || targetMode != request.getTargetMode()) {
            return false;
        }

        if (targetMode == TargetMode.VINTED_MODEL) {
            return sameNormalizedText(model, request.getModel());
        }
        if (targetMode == TargetMode.SEARCH_QUERY) {
            return sameNormalizedText(searchQuery, request.getSearchQuery());
        }
        return false;
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

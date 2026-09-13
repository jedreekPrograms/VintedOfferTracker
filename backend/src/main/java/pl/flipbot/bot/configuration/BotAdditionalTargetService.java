package pl.flipbot.bot.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.dto.BotAdditionalTargetResponse;
import pl.flipbot.bot.dto.UpsertBotAdditionalTargetRequest;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.mapper.BotAdditionalTargetMapper;
import pl.flipbot.negotiation.NegotiationReactionAction;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.SellerCounterOfferRule;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;
import pl.flipbot.negotiation.dto.SellerCounterOfferRuleRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BotAdditionalTargetService {

    public static final int MAX_ADDITIONAL_TARGETS = 4;

    private static final int MAX_RESPONSE_WAIT_HOURS = 24 * 30;

    private final BotRepository botRepository;
    private final BotAdditionalTargetRepository additionalTargetRepository;
    private final BotAdditionalTargetMapper additionalTargetMapper;

    @Transactional(readOnly = true)
    public List<BotAdditionalTargetResponse> listActive(Long botId) {
        requireBot(botId);
        return additionalTargetRepository
                .findAllByConfigurationBotIdAndActiveTrueOrderByIdAsc(botId)
                .stream()
                .map(additionalTargetMapper::map)
                .toList();
    }

    @Transactional
    public BotAdditionalTargetResponse create(
            Long botId,
            UpsertBotAdditionalTargetRequest request
    ) {
        Bot bot = requireEditableBot(botId);

        long activeTargets = additionalTargetRepository
                .countByConfigurationBotIdAndActiveTrue(botId);
        if (activeTargets >= MAX_ADDITIONAL_TARGETS) {
            throw new IllegalStateException(
                    "Bot może mieć maksymalnie 4 dodatkowe produkty (5 produktów łącznie z głównym)."
            );
        }

        validateRequest(request);

        BotAdditionalTarget target = BotAdditionalTarget.builder()
                .configuration(bot.getConfiguration())
                .active(true)
                .build();

        applyRequest(target, request);
        return additionalTargetMapper.map(
                additionalTargetRepository.save(target)
        );
    }

    @Transactional
    public BotAdditionalTargetResponse update(
            Long botId,
            Long targetId,
            UpsertBotAdditionalTargetRequest request
    ) {
        requireEditableBot(botId);
        validateRequest(request);

        BotAdditionalTarget target = requireTarget(botId, targetId);
        if (!Boolean.TRUE.equals(target.getActive())) {
            throw new IllegalStateException(
                    "Wyłączony dodatkowy produkt nie może być edytowany. Dodaj go ponownie jako nowy produkt."
            );
        }

        applyRequest(target, request);
        return additionalTargetMapper.map(target);
    }

    @Transactional
    public void deactivate(Long botId, Long targetId) {
        requireEditableBot(botId);
        BotAdditionalTarget target = requireTarget(botId, targetId);
        target.setActive(false);
    }

    @Transactional(readOnly = true)
    public List<BotAdditionalTarget> findAllForPlaywright(Long botId) {
        return additionalTargetRepository
                .findAllByConfigurationBotIdOrderByIdAsc(botId);
    }

    private Bot requireBot(Long botId) {
        return botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException(botId));
    }

    private Bot requireEditableBot(Long botId) {
        Bot bot = requireBot(botId);
        if (bot.getStatus() != BotStatus.STOPPED) {
            throw new IllegalStateException(
                    "Najpierw zatrzymaj bota, aby zmieniać dodatkowe produkty."
            );
        }
        if (bot.getConfiguration() == null) {
            throw new IllegalStateException("Bot nie ma konfiguracji głównego produktu.");
        }
        return bot;
    }

    private BotAdditionalTarget requireTarget(Long botId, Long targetId) {
        return additionalTargetRepository
                .findByIdAndConfigurationBotId(targetId, botId)
                .orElseThrow(() -> new java.util.NoSuchElementException(
                        "Nie znaleziono dodatkowego produktu " + targetId
                                + " dla bota " + botId + "."
                ));
    }

    private void applyRequest(
            BotAdditionalTarget target,
            UpsertBotAdditionalTargetRequest request
    ) {
        target.setCategoryPath(new ArrayList<>(request.getCategoryPath()));
        target.setBrand(request.getBrand().trim());
        target.setTargetMode(request.getTargetMode());
        target.setModel(normalize(request.getModel()));
        target.setSearchQuery(normalize(request.getSearchQuery()));
        target.setMinPrice(request.getMinPrice());
        target.setMaxPrice(request.getMaxPrice());
        target.setAutoRaiseOfferToVintedMinimum(
                Boolean.TRUE.equals(request.getAutoRaiseOfferToVintedMinimum())
        );
        target.setMaxAutomaticOffer(request.getMaxAutomaticOffer());

        target.getNegotiationSteps().clear();
        int stepNumber = 1;
        for (CreateNegotiationStepRequest stepRequest : request.getNegotiationSteps()) {
            NegotiationStep step = buildStep(
                    target,
                    stepNumber++,
                    stepRequest
            );
            target.getNegotiationSteps().add(step);
        }
    }

    private NegotiationStep buildStep(
            BotAdditionalTarget target,
            int stepNumber,
            CreateNegotiationStepRequest request
    ) {
        NegotiationReactionAction rejectionAction = request.getRejectionAction() == null
                ? NegotiationReactionAction.NEXT_STEP_NOW
                : request.getRejectionAction();
        NegotiationReactionAction counterAction = request.getCounterOfferDefaultAction() == null
                ? NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
                : request.getCounterOfferDefaultAction();

        return NegotiationStep.builder()
                .stepNumber(stepNumber)
                .offerPrice(request.getOfferPrice())
                .maxAcceptedCounterOffer(request.getMaxAcceptedCounterOffer())
                .message(request.getMessage().trim())
                .rejectionAction(rejectionAction)
                .rejectionWaitHours(resolveWaitHours(
                        rejectionAction,
                        request.getRejectionWaitHours(),
                        6
                ))
                .counterOfferDefaultAction(counterAction)
                .counterOfferDefaultWaitHours(resolveWaitHours(
                        counterAction,
                        request.getCounterOfferDefaultWaitHours(),
                        6
                ))
                .counterOfferRules(mapCounterOfferRules(request.getCounterOfferRules()))
                .additionalTarget(target)
                .build();
    }

    private List<SellerCounterOfferRule> mapCounterOfferRules(
            List<SellerCounterOfferRuleRequest> requests
    ) {
        if (requests == null) {
            return List.of(
                    SellerCounterOfferRule.builder()
                            .minimumDiscountPercent(new BigDecimal("10"))
                            .action(NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP)
                            .waitHours(2)
                            .build(),
                    SellerCounterOfferRule.builder()
                            .minimumDiscountPercent(new BigDecimal("15"))
                            .action(NegotiationReactionAction.NEXT_STEP_NOW)
                            .waitHours(null)
                            .build()
            );
        }

        return requests.stream()
                .map(rule -> SellerCounterOfferRule.builder()
                        .minimumDiscountPercent(rule.getMinimumDiscountPercent())
                        .action(rule.getAction())
                        .waitHours(resolveWaitHours(
                                rule.getAction(),
                                rule.getWaitHours(),
                                2
                        ))
                        .build())
                .toList();
    }

    private Integer resolveWaitHours(
            NegotiationReactionAction action,
            Integer configured,
            int fallback
    ) {
        if (action == NegotiationReactionAction.NEXT_STEP_NOW) {
            return null;
        }
        return configured == null ? fallback : configured;
    }

    private void validateRequest(UpsertBotAdditionalTargetRequest request) {
        if (request.getMinPrice().signum() < 0
                || request.getMaxPrice().signum() < 0) {
            throw new IllegalStateException("Zakres cen nie może zawierać wartości ujemnych.");
        }

        if (request.getNegotiationSteps() == null
                || request.getNegotiationSteps().isEmpty()) {
            throw new IllegalStateException("Dodatkowy produkt musi mieć co najmniej jeden krok negocjacji.");
        }

        for (CreateNegotiationStepRequest step : request.getNegotiationSteps()) {
            if (step.getOfferPrice() == null || step.getOfferPrice().signum() <= 0) {
                throw new IllegalStateException("Cena każdego kroku negocjacji musi być większa od zera.");
            }
            if (step.getMaxAcceptedCounterOffer() == null
                    || step.getMaxAcceptedCounterOffer().signum() <= 0) {
                throw new IllegalStateException("Próg akceptowanej kontroferty musi być większy od zera.");
            }
            validateWait(step.getRejectionAction(), step.getRejectionWaitHours());
            validateWait(
                    step.getCounterOfferDefaultAction(),
                    step.getCounterOfferDefaultWaitHours()
            );
            if (step.getCounterOfferRules() != null) {
                for (SellerCounterOfferRuleRequest rule : step.getCounterOfferRules()) {
                    validateWait(rule.getAction(), rule.getWaitHours());
                }
            }
        }
    }

    private void validateWait(
            NegotiationReactionAction action,
            Integer waitHours
    ) {
        if (action != NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP) {
            return;
        }
        if (waitHours != null
                && (waitHours < 1 || waitHours > MAX_RESPONSE_WAIT_HOURS)) {
            throw new IllegalStateException(
                    "Czas oczekiwania musi mieścić się w zakresie 1-720 godzin."
            );
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

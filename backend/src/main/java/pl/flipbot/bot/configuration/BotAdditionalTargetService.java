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
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.mapper.BotAdditionalTargetMapper;
import pl.flipbot.negotiation.NegotiationReactionAction;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.SellerCounterOfferRule;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;
import pl.flipbot.negotiation.dto.SellerCounterOfferRuleRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class BotAdditionalTargetService {

    public static final int MAX_ADDITIONAL_TARGETS = 4;

    private static final int MAX_RESPONSE_WAIT_HOURS = 24 * 30;
    private static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("100");

    private final BotRepository botRepository;
    private final BotAdditionalTargetRepository additionalTargetRepository;
    private final ListingRepository listingRepository;
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

        applyDefinitionFields(target, request);
        replaceNegotiationSteps(target, request.getNegotiationSteps());

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

        boolean targetDefinitionChanged = targetDefinitionChanged(target, request);
        boolean priceRangeChanged = !sameDecimal(target.getMinPrice(), request.getMinPrice())
                || !sameDecimal(target.getMaxPrice(), request.getMaxPrice());
        boolean requestedAdaptive = Boolean.TRUE.equals(
                request.getAutoRaiseOfferToVintedMinimum()
        );
        boolean adaptiveModeChanged = Boolean.TRUE.equals(
                target.getAutoRaiseOfferToVintedMinimum()
        ) != requestedAdaptive;
        boolean capIncreased = isGlobalCapIncreased(
                target.getMaxAutomaticOffer(),
                requestedAdaptive ? request.getMaxAutomaticOffer() : null
        );
        boolean stepDefinitionChanged = negotiationStepDefinitionChanged(
                target,
                request.getNegotiationSteps()
        );
        boolean responsePoliciesChanged = negotiationResponsePoliciesChanged(
                target,
                request.getNegotiationSteps()
        );

        List<Listing> activeListings = getActiveNegotiationListings(
                botId,
                targetId
        );

        validateActiveNegotiationEdit(
                activeListings,
                targetDefinitionChanged,
                adaptiveModeChanged,
                stepDefinitionChanged
        );

        applyDefinitionFields(target, request);

        if (stepDefinitionChanged) {
            replaceNegotiationSteps(target, request.getNegotiationSteps());
        } else if (responsePoliciesChanged) {
            applyResponsePolicies(target, request.getNegotiationSteps());
        }

        if (stepDefinitionChanged || adaptiveModeChanged || capIncreased) {
            resetTargetListingsWithStatus(
                    botId,
                    targetId,
                    ListingStatus.SKIPPED_OFFER_TOO_LOW
            );
        }
        if (priceRangeChanged) {
            resetTargetListingsWithStatus(
                    botId,
                    targetId,
                    ListingStatus.SKIPPED_OUTSIDE_PRICE_RANGE
            );
        }
        if (targetDefinitionChanged) {
            resetTargetListingsWithStatus(
                    botId,
                    targetId,
                    ListingStatus.SKIPPED_TARGET_MISMATCH
            );
        }

        return additionalTargetMapper.map(target);
    }

    /**
     * Deactivation stops future catalog scans only. The row and its negotiation
     * ladder intentionally remain in the database so existing conversations
     * continue with the exact product strategy that started them.
     */
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

    private List<Listing> getActiveNegotiationListings(
            Long botId,
            Long targetId
    ) {
        List<Listing> active = new ArrayList<>(
                listingRepository.findByBotIdAndStatusAndAdditionalTargetIdOrderByIdAsc(
                        botId,
                        ListingStatus.NEGOTIATING,
                        targetId
                )
        );
        active.addAll(
                listingRepository.findByBotIdAndStatusAndAdditionalTargetIdOrderByIdAsc(
                        botId,
                        ListingStatus.ACTION_REQUIRED,
                        targetId
                )
        );
        return active;
    }

    private void validateActiveNegotiationEdit(
            List<Listing> activeListings,
            boolean targetDefinitionChanged,
            boolean adaptiveModeChanged,
            boolean stepDefinitionChanged
    ) {
        if (activeListings.isEmpty()) {
            return;
        }

        List<String> lockedChanges = new ArrayList<>();
        if (targetDefinitionChanged) {
            lockedChanges.add("kategoria/marka/model lub fraza wyszukiwania");
        }
        if (adaptiveModeChanged) {
            lockedChanges.add("tryb adaptacyjnej ceny");
        }
        if (stepDefinitionChanged) {
            lockedChanges.add("ceny/progi/wiadomości lub liczba kroków negocjacji");
        }

        if (!lockedChanges.isEmpty()) {
            throw new IllegalStateException(
                    "Ten dodatkowy produkt ma aktywne negocjacje. Do ich zakończenia nie można zmienić: "
                            + String.join(", ", lockedChanges)
                            + ". Nadal można zmienić zakres cen nowych ogłoszeń, globalny limit przyszłych ofert oraz reguły/czasy reakcji. Produkt można też wyłączyć, co zatrzyma tylko nowe skany."
            );
        }
    }

    private void applyDefinitionFields(
            BotAdditionalTarget target,
            UpsertBotAdditionalTargetRequest request
    ) {
        TargetMode mode = request.getTargetMode();
        boolean adaptive = Boolean.TRUE.equals(
                request.getAutoRaiseOfferToVintedMinimum()
        );

        target.setCategoryPath(normalizeCategoryPath(request.getCategoryPath()));
        target.setBrand(normalizeRequired(request.getBrand()));
        target.setTargetMode(mode);
        target.setModel(mode == TargetMode.VINTED_MODEL
                ? normalizeRequired(request.getModel())
                : null);
        target.setSearchQuery(mode == TargetMode.SEARCH_QUERY
                ? normalizeRequired(request.getSearchQuery())
                : null);
        target.setMinPrice(request.getMinPrice());
        target.setMaxPrice(request.getMaxPrice());
        target.setAutoRaiseOfferToVintedMinimum(adaptive);
        target.setMaxAutomaticOffer(adaptive
                ? request.getMaxAutomaticOffer()
                : null);
    }

    private void replaceNegotiationSteps(
            BotAdditionalTarget target,
            List<CreateNegotiationStepRequest> requests
    ) {
        target.getNegotiationSteps().clear();

        for (int index = 0; index < requests.size(); index++) {
            CreateNegotiationStepRequest request = requests.get(index);
            ResolvedStepPolicy policy = resolvePolicy(request, index + 1);

            target.getNegotiationSteps().add(
                    NegotiationStep.builder()
                            .stepNumber(index + 1)
                            .offerPrice(request.getOfferPrice())
                            .maxAcceptedCounterOffer(request.getMaxAcceptedCounterOffer())
                            .message(normalizeRequired(request.getMessage()))
                            .rejectionAction(policy.rejectionAction())
                            .rejectionWaitHours(policy.rejectionWaitHours())
                            .counterOfferDefaultAction(policy.counterDefaultAction())
                            .counterOfferDefaultWaitHours(policy.counterDefaultWaitHours())
                            .counterOfferRules(toRuleEntities(policy.rules()))
                            .additionalTarget(target)
                            .build()
            );
        }
    }

    private void applyResponsePolicies(
            BotAdditionalTarget target,
            List<CreateNegotiationStepRequest> requests
    ) {
        List<NegotiationStep> existing = orderedSteps(target);
        if (existing.size() != requests.size()) {
            throw new IllegalStateException(
                    "Nie można zapisać polityk reakcji, ponieważ zmieniła się struktura kroków."
            );
        }

        for (int index = 0; index < existing.size(); index++) {
            NegotiationStep step = existing.get(index);
            ResolvedStepPolicy policy = resolvePolicy(requests.get(index), index + 1);

            step.setRejectionAction(policy.rejectionAction());
            step.setRejectionWaitHours(policy.rejectionWaitHours());
            step.setCounterOfferDefaultAction(policy.counterDefaultAction());
            step.setCounterOfferDefaultWaitHours(policy.counterDefaultWaitHours());
            step.getCounterOfferRules().clear();
            step.getCounterOfferRules().addAll(toRuleEntities(policy.rules()));
        }
    }

    private boolean negotiationStepDefinitionChanged(
            BotAdditionalTarget target,
            List<CreateNegotiationStepRequest> requested
    ) {
        List<NegotiationStep> existing = orderedSteps(target);
        if (requested == null || existing.size() != requested.size()) {
            return true;
        }

        for (int index = 0; index < existing.size(); index++) {
            NegotiationStep left = existing.get(index);
            CreateNegotiationStepRequest right = requested.get(index);

            if (!Objects.equals(left.getStepNumber(), index + 1)
                    || !sameDecimal(left.getOfferPrice(), right.getOfferPrice())
                    || !sameDecimal(
                    left.getMaxAcceptedCounterOffer(),
                    right.getMaxAcceptedCounterOffer()
            )
                    || !Objects.equals(
                    normalizeRequired(left.getMessage()),
                    normalizeRequired(right.getMessage())
            )) {
                return true;
            }
        }
        return false;
    }

    private boolean negotiationResponsePoliciesChanged(
            BotAdditionalTarget target,
            List<CreateNegotiationStepRequest> requested
    ) {
        List<NegotiationStep> existing = orderedSteps(target);
        if (requested == null || existing.size() != requested.size()) {
            return true;
        }

        for (int index = 0; index < existing.size(); index++) {
            if (!samePolicy(
                    existing.get(index),
                    resolvePolicy(requested.get(index), index + 1)
            )) {
                return true;
            }
        }
        return false;
    }

    private boolean samePolicy(
            NegotiationStep existing,
            ResolvedStepPolicy requested
    ) {
        if (existing.getRejectionAction() != requested.rejectionAction()
                || !Objects.equals(
                existing.getRejectionWaitHours(),
                requested.rejectionWaitHours()
        )
                || existing.getCounterOfferDefaultAction() != requested.counterDefaultAction()
                || !Objects.equals(
                existing.getCounterOfferDefaultWaitHours(),
                requested.counterDefaultWaitHours()
        )) {
            return false;
        }

        List<CounterRuleValue> leftRules = existing.getCounterOfferRules().stream()
                .map(rule -> new CounterRuleValue(
                        rule.getMinimumDiscountPercent(),
                        rule.getAction(),
                        rule.getWaitHours()
                ))
                .sorted(Comparator.comparing(CounterRuleValue::minimumDiscountPercent))
                .toList();
        List<CounterRuleValue> rightRules = requested.rules().stream()
                .sorted(Comparator.comparing(CounterRuleValue::minimumDiscountPercent))
                .toList();

        if (leftRules.size() != rightRules.size()) {
            return false;
        }
        for (int index = 0; index < leftRules.size(); index++) {
            CounterRuleValue left = leftRules.get(index);
            CounterRuleValue right = rightRules.get(index);
            if (!sameDecimal(left.minimumDiscountPercent(), right.minimumDiscountPercent())
                    || left.action() != right.action()
                    || !Objects.equals(left.waitHours(), right.waitHours())) {
                return false;
            }
        }
        return true;
    }

    private List<NegotiationStep> orderedSteps(BotAdditionalTarget target) {
        return target.getNegotiationSteps().stream()
                .sorted(Comparator.comparing(
                        step -> step.getStepNumber() == null
                                ? Integer.MAX_VALUE
                                : step.getStepNumber()
                ))
                .toList();
    }

    private boolean targetDefinitionChanged(
            BotAdditionalTarget target,
            UpsertBotAdditionalTargetRequest request
    ) {
        if (!categoryPathsEqual(target.getCategoryPath(), request.getCategoryPath())
                || !sameNormalizedText(target.getBrand(), request.getBrand())
                || target.getTargetMode() != request.getTargetMode()) {
            return true;
        }

        if (request.getTargetMode() == TargetMode.VINTED_MODEL) {
            return !sameNormalizedText(target.getModel(), request.getModel());
        }
        return !sameNormalizedText(target.getSearchQuery(), request.getSearchQuery());
    }

    private void resetTargetListingsWithStatus(
            Long botId,
            Long targetId,
            ListingStatus status
    ) {
        for (Listing listing :
                listingRepository.findByBotIdAndStatusAndAdditionalTargetIdOrderByIdAsc(
                        botId,
                        status,
                        targetId
                )) {
            listing.setStatus(ListingStatus.DISCOVERED);
        }
    }

    private void validateRequest(UpsertBotAdditionalTargetRequest request) {
        Objects.requireNonNull(request, "Dodatkowy produkt jest wymagany.");

        if (request.getCategoryPath() == null
                || request.getCategoryPath().isEmpty()
                || request.getCategoryPath().stream()
                .anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Kategoria dodatkowego produktu jest wymagana.");
        }
        if (request.getBrand() == null || request.getBrand().isBlank()) {
            throw new IllegalArgumentException("Marka dodatkowego produktu jest wymagana.");
        }
        if (request.getTargetMode() == null) {
            throw new IllegalArgumentException("Tryb celu dodatkowego produktu jest wymagany.");
        }
        if (request.getTargetMode() == TargetMode.VINTED_MODEL
                && (request.getModel() == null || request.getModel().isBlank())) {
            throw new IllegalArgumentException("Model jest wymagany w trybie VINTED_MODEL.");
        }
        if (request.getTargetMode() == TargetMode.SEARCH_QUERY
                && (request.getSearchQuery() == null || request.getSearchQuery().isBlank())) {
            throw new IllegalArgumentException("Fraza wyszukiwania jest wymagana w trybie SEARCH_QUERY.");
        }

        validatePriceRange(request.getMinPrice(), request.getMaxPrice());

        if (request.getNegotiationSteps() == null
                || request.getNegotiationSteps().isEmpty()) {
            throw new IllegalArgumentException(
                    "Dodatkowy produkt musi mieć co najmniej jeden krok negocjacji."
            );
        }
        if (request.getNegotiationSteps().size() > 25) {
            throw new IllegalArgumentException(
                    "Dodatkowy produkt może mieć maksymalnie 25 kroków negocjacji."
            );
        }

        boolean adaptive = Boolean.TRUE.equals(
                request.getAutoRaiseOfferToVintedMinimum()
        );
        if (adaptive) {
            if (request.getMaxAutomaticOffer() == null
                    || request.getMaxAutomaticOffer().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Globalny limit negocjacji musi być większy od zera."
                );
            }
            if (request.getMaxAutomaticOffer().compareTo(request.getMaxPrice()) > 0) {
                throw new IllegalArgumentException(
                        "Globalny limit negocjacji nie może przekraczać maksymalnej ceny ogłoszenia."
                );
            }
        }

        BigDecimal previousOffer = null;
        for (int index = 0; index < request.getNegotiationSteps().size(); index++) {
            CreateNegotiationStepRequest step = request.getNegotiationSteps().get(index);
            int stepNumber = index + 1;
            if (step == null
                    || step.getOfferPrice() == null
                    || step.getOfferPrice().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Cena kroku " + stepNumber + " musi być większa od zera."
                );
            }
            if (step.getMaxAcceptedCounterOffer() == null
                    || step.getMaxAcceptedCounterOffer().signum() <= 0) {
                throw new IllegalArgumentException(
                        "Próg akceptowanej kontroferty kroku " + stepNumber
                                + " musi być większy od zera."
                );
            }
            if (step.getMessage() == null || step.getMessage().isBlank()) {
                throw new IllegalArgumentException(
                        "Wiadomość kroku " + stepNumber + " jest wymagana."
                );
            }
            if (adaptive
                    && previousOffer != null
                    && step.getOfferPrice().compareTo(previousOffer) <= 0) {
                throw new IllegalArgumentException(
                        "Ceny kroków muszą rosnąć w trybie adaptacyjnym."
                );
            }
            previousOffer = step.getOfferPrice();

            validateResolvedPolicy(resolvePolicy(step, stepNumber), stepNumber);
        }

        if (adaptive
                && request.getMaxAutomaticOffer().compareTo(
                request.getNegotiationSteps().getFirst().getOfferPrice()
        ) < 0) {
            throw new IllegalArgumentException(
                    "Globalny limit negocjacji nie może być niższy niż pierwszy krok."
            );
        }
    }

    private void validatePriceRange(
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        if (minPrice == null || maxPrice == null) {
            throw new IllegalArgumentException("Minimalna i maksymalna cena są wymagane.");
        }
        if (minPrice.signum() < 0 || maxPrice.signum() < 0) {
            throw new IllegalArgumentException("Zakres cen nie może zawierać wartości ujemnych.");
        }
        if (minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                    "Cena minimalna nie może być wyższa od ceny maksymalnej."
            );
        }
    }

    private void validateResolvedPolicy(
            ResolvedStepPolicy policy,
            int stepNumber
    ) {
        validateReaction(
                policy.rejectionAction(),
                policy.rejectionWaitHours(),
                "Krok " + stepNumber + " po odrzuceniu"
        );
        validateReaction(
                policy.counterDefaultAction(),
                policy.counterDefaultWaitHours(),
                "Krok " + stepNumber + " domyślna kontroferta"
        );

        Set<String> thresholds = new HashSet<>();
        for (CounterRuleValue rule : policy.rules()) {
            if (rule.minimumDiscountPercent() == null
                    || rule.minimumDiscountPercent().signum() <= 0
                    || rule.minimumDiscountPercent().compareTo(MAX_DISCOUNT_PERCENT) > 0) {
                throw new IllegalArgumentException(
                        "Próg procentowy kroku " + stepNumber
                                + " musi być większy od 0 i nie większy niż 100%."
                );
            }
            String normalized = rule.minimumDiscountPercent()
                    .stripTrailingZeros()
                    .toPlainString();
            if (!thresholds.add(normalized)) {
                throw new IllegalArgumentException(
                        "Krok " + stepNumber + " zawiera powtórzony próg "
                                + normalized + "%."
                );
            }
            validateReaction(
                    rule.action(),
                    rule.waitHours(),
                    "Krok " + stepNumber + " próg " + normalized + "%"
            );
        }
    }

    private void validateReaction(
            NegotiationReactionAction action,
            Integer waitHours,
            String label
    ) {
        if (action == null) {
            throw new IllegalArgumentException(label + " nie ma ustawionej akcji.");
        }
        if (action == NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
                && (waitHours == null
                || waitHours < 1
                || waitHours > MAX_RESPONSE_WAIT_HOURS)) {
            throw new IllegalArgumentException(
                    label + " wymaga czasu 1-720 godzin."
            );
        }
    }

    private ResolvedStepPolicy resolvePolicy(
            CreateNegotiationStepRequest request,
            int stepNumber
    ) {
        NegotiationReactionAction rejectionAction = request.getRejectionAction();
        Integer rejectionWait = request.getRejectionWaitHours();
        if (rejectionAction == null) {
            if (stepNumber == 1) {
                rejectionAction = NegotiationReactionAction.NEXT_STEP_NOW;
                rejectionWait = null;
            } else {
                rejectionAction = NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP;
                rejectionWait = defaultRejectionWaitHours(stepNumber);
            }
        }
        if (rejectionAction == NegotiationReactionAction.NEXT_STEP_NOW) {
            rejectionWait = null;
        }

        NegotiationReactionAction counterAction = request.getCounterOfferDefaultAction();
        Integer counterWait = request.getCounterOfferDefaultWaitHours();
        if (counterAction == null) {
            counterAction = NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP;
            counterWait = 6;
        }
        if (counterAction == NegotiationReactionAction.NEXT_STEP_NOW) {
            counterWait = null;
        }

        List<CounterRuleValue> rules;
        if (request.getCounterOfferRules() == null) {
            rules = defaultCounterOfferRules();
        } else {
            rules = request.getCounterOfferRules().stream()
                    .filter(Objects::nonNull)
                    .map(this::toRuleValue)
                    .toList();
        }

        return new ResolvedStepPolicy(
                rejectionAction,
                rejectionWait,
                counterAction,
                counterWait,
                rules
        );
    }

    private CounterRuleValue toRuleValue(SellerCounterOfferRuleRequest request) {
        NegotiationReactionAction action = request.getAction();
        Integer wait = request.getWaitHours();
        if (action == NegotiationReactionAction.NEXT_STEP_NOW) {
            wait = null;
        }
        return new CounterRuleValue(
                request.getMinimumDiscountPercent(),
                action,
                wait
        );
    }

    private List<CounterRuleValue> defaultCounterOfferRules() {
        return List.of(
                new CounterRuleValue(
                        new BigDecimal("10"),
                        NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP,
                        2
                ),
                new CounterRuleValue(
                        new BigDecimal("15"),
                        NegotiationReactionAction.NEXT_STEP_NOW,
                        null
                )
        );
    }

    private int defaultRejectionWaitHours(int stepNumber) {
        if (stepNumber == 2) {
            return 6;
        }
        if (stepNumber == 3) {
            return 12;
        }
        return 24;
    }

    private List<SellerCounterOfferRule> toRuleEntities(
            List<CounterRuleValue> rules
    ) {
        List<SellerCounterOfferRule> result = new ArrayList<>();
        rules.stream()
                .sorted(Comparator.comparing(CounterRuleValue::minimumDiscountPercent))
                .forEach(rule -> result.add(
                        SellerCounterOfferRule.builder()
                                .minimumDiscountPercent(rule.minimumDiscountPercent())
                                .action(rule.action())
                                .waitHours(rule.waitHours())
                                .build()
                ));
        return result;
    }

    private boolean categoryPathsEqual(
            List<String> left,
            List<String> right
    ) {
        return normalizeCategoryPath(left).equals(normalizeCategoryPath(right));
    }

    private List<String> normalizeCategoryPath(List<String> path) {
        if (path == null) {
            return List.of();
        }
        return path.stream()
                .map(this::normalizeRequired)
                .toList();
    }

    private boolean sameNormalizedText(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        return normalizeRequired(left).equalsIgnoreCase(normalizeRequired(right));
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private boolean isGlobalCapIncreased(
            BigDecimal current,
            BigDecimal requested
    ) {
        if (requested == null) {
            return false;
        }
        return current == null || requested.compareTo(current) > 0;
    }

    private String normalizeRequired(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

    private record ResolvedStepPolicy(
            NegotiationReactionAction rejectionAction,
            Integer rejectionWaitHours,
            NegotiationReactionAction counterDefaultAction,
            Integer counterDefaultWaitHours,
            List<CounterRuleValue> rules
    ) {
    }

    private record CounterRuleValue(
            BigDecimal minimumDiscountPercent,
            NegotiationReactionAction action,
            Integer waitHours
    ) {
    }
}

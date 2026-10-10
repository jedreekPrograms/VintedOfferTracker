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
import pl.flipbot.negotiation.NegotiationPolicyDefaults.ResolvedStepPolicy;
import pl.flipbot.negotiation.NegotiationStepPolicySupport;
import pl.flipbot.negotiation.NegotiationResponsePolicyValidator;
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;

import static pl.flipbot.negotiation.NegotiationPolicyDefaults.resolvePolicy;
import static pl.flipbot.negotiation.NegotiationStepPolicySupport.sameDecimal;
import static pl.flipbot.negotiation.NegotiationStepPolicySupport.nextStrategyVersion;
import static pl.flipbot.negotiation.NegotiationStepPolicySupport.isGlobalCapIncreased;
import static pl.flipbot.negotiation.NegotiationStepPolicySupport.applyPolicy;
import static pl.flipbot.negotiation.NegotiationStepPolicySupport.toRuleEntities;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BotAdditionalTargetService {

    public static final int MAX_ADDITIONAL_TARGETS = 4;


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
        BigDecimal requestedGlobalCap = requestedAdaptive
                ? request.getMaxAutomaticOffer()
                : null;
        boolean globalCapChanged = !sameDecimal(
                target.getMaxAutomaticOffer(),
                requestedGlobalCap
        );
        boolean capIncreased = isGlobalCapIncreased(
                target.getMaxAutomaticOffer(),
                requestedGlobalCap
        );
        boolean stepDefinitionChanged = NegotiationStepPolicySupport.definitionChanged(orderedSteps(target), request.getNegotiationSteps(), this::normalizeRequired);
        boolean responsePoliciesChanged = NegotiationStepPolicySupport.responsePoliciesChanged(orderedSteps(target), request.getNegotiationSteps());
        boolean strategyChanged = adaptiveModeChanged
                || globalCapChanged
                || stepDefinitionChanged
                || responsePoliciesChanged;

        List<Listing> activeListings = getActiveNegotiationListings(
                botId,
                targetId
        );

        validateActiveNegotiationEdit(
                activeListings,
                targetDefinitionChanged
        );

        applyDefinitionFields(target, request);

        if (stepDefinitionChanged) {
            replaceNegotiationSteps(target, request.getNegotiationSteps());
        } else if (responsePoliciesChanged) {
            applyResponsePolicies(target, request.getNegotiationSteps());
        }

        if (strategyChanged) {
            target.setNegotiationStrategyVersion(
                    nextStrategyVersion(target.getNegotiationStrategyVersion())
            );
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
            boolean targetDefinitionChanged
    ) {
        if (activeListings.isEmpty()) {
            return;
        }

        if (targetDefinitionChanged) {
            throw new IllegalStateException(
                    "Ten dodatkowy produkt ma aktywne negocjacje, więc nie można teraz zmienić jego kategorii, marki, modelu ani frazy wyszukiwania. "
                            + "Strategia negocjacji jest wersjonowana: ceny, progi, wiadomości, liczba kroków, tryb adaptacyjny, globalny limit i reguły reakcji można zmienić bez wpływu na rozpoczęte rozmowy."
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

            applyPolicy(step, policy);
        }
    }





    private List<NegotiationStep> orderedSteps(BotAdditionalTarget target) {
        return NegotiationStepPolicySupport.orderedSteps(target.getNegotiationSteps());
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





    private void validateResolvedPolicy(ResolvedStepPolicy policy, int stepNumber) {
        NegotiationResponsePolicyValidator.validate(
                policy, stepNumber, NegotiationResponsePolicyValidator.MessageStyle.ADDITIONAL
        );
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







    private String normalizeRequired(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replaceAll("\\s+", " ");
    }

}

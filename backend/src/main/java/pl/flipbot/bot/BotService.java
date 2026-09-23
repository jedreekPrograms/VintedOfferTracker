package pl.flipbot.bot;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.bot.dto.*;
import pl.flipbot.bot.runtime.BotSessionPreviewService;
import pl.flipbot.exception.BotAlreadyExistsException;
import pl.flipbot.exception.BotNotFoundException;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.mapper.BotMapper;
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
public class BotService {
    private static final int MAX_RESPONSE_WAIT_HOURS = 24 * 30;
    private static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("100");

    private final BotRepository botRepository;
    private final BotConfigurationRepository botConfigurationRepository;
    private final ListingRepository listingRepository;
    private final BotMapper botMapper;
    private final BotSessionPreviewService sessionPreviewService;

    public List<BotResponse> getAllBots() {
        return botRepository.findAll().stream().map(botMapper::map).toList();
    }

    public BotResponse getBot(Long botId) {
        return botMapper.map(getBotEntity(botId));
    }

    public BotEditCapabilitiesResponse getEditCapabilities(Long botId) {
        Bot bot = getBotEntity(botId);
        List<Listing> active = getActiveNegotiationListings(botId);
        List<Listing> mainActive = getMainProductActiveNegotiationListings(botId);
        return BotEditCapabilitiesResponse.builder()
                .hasActiveNegotiations(!active.isEmpty())
                .hasMainProductActiveNegotiations(!mainActive.isEmpty())
                .minimumNegotiationCap(minimumNegotiationCap(bot))
                .build();
    }

    @Transactional
    public BotResponse createBot(CreateBotRequest request) {
        if (botRepository.existsByEmail(request.getEmail())) {
            throw new BotAlreadyExistsException(request.getEmail());
        }
        CreateBotConfigurationRequest requested = request.getConfiguration();
        validateConfiguration(requested);
        TargetMode mode = resolveTargetMode(requested);
        boolean adaptive = Boolean.TRUE.equals(requested.getAutoRaiseOfferToVintedMinimum());
        Bot bot = Bot.builder()
                .name(normalizeRequiredText(request.getName()))
                .email(normalizeRequiredText(request.getEmail()))
                .password(request.getPassword())
                .status(BotStatus.STOPPED)
                .build();
        Bot savedBot = botRepository.save(bot);
        BotConfiguration configuration = BotConfiguration.builder()
                .marketplace(requested.getMarketplace())
                .categoryPath(new ArrayList<>(requested.getCategoryPath()))
                .brand(normalizeRequiredText(requested.getBrand()))
                .targetMode(mode)
                .model(mode == TargetMode.VINTED_MODEL ? normalizeRequiredText(requested.getModel()) : null)
                .searchQuery(mode == TargetMode.SEARCH_QUERY ? normalizeRequiredText(requested.getSearchQuery()) : null)
                .minPrice(requested.getMinPrice())
                .maxPrice(requested.getMaxPrice())
                .autoRaiseOfferToVintedMinimum(adaptive)
                .maxAutomaticOffer(adaptive ? requested.getMaxAutomaticOffer() : null)
                .dailyNegotiationBudget(requested.getDailyNegotiationBudget())
                .bot(savedBot)
                .build();
        savedBot.setConfiguration(configuration);
        replaceNegotiationSteps(configuration, requested.getNegotiationSteps());
        botConfigurationRepository.save(configuration);
        return botMapper.map(savedBot);
    }

    @Transactional
    public BotResponse updateBot(Long botId, UpdateBotRequest request) {
        Bot bot = getBotEntity(botId);
        if (bot.getStatus() != BotStatus.STOPPED) {
            throw new IllegalStateException("Only a stopped bot can be edited. Stop the bot first.");
        }
        BotConfiguration configuration = bot.getConfiguration();
        if (configuration == null) {
            throw new IllegalStateException("Bot configuration does not exist.");
        }
        CreateBotConfigurationRequest requested = request.getConfiguration();
        validateConfiguration(requested);
        TargetMode requestedMode = resolveTargetMode(requested);
        boolean requestedAdaptive = Boolean.TRUE.equals(requested.getAutoRaiseOfferToVintedMinimum());
        String normalizedEmail = normalizeRequiredText(request.getEmail());
        boolean stepDefinitionChanged = negotiationStepDefinitionChanged(configuration, requested.getNegotiationSteps());
        boolean responsePoliciesChanged = negotiationResponsePoliciesChanged(configuration, requested.getNegotiationSteps());
        boolean priceRangeChanged = !sameDecimal(configuration.getMinPrice(), requested.getMinPrice())
                || !sameDecimal(configuration.getMaxPrice(), requested.getMaxPrice());
        boolean adaptiveModeChanged = Boolean.TRUE.equals(configuration.getAutoRaiseOfferToVintedMinimum()) != requestedAdaptive;
        boolean globalCapIncreased = isGlobalCapIncreased(
                configuration.getMaxAutomaticOffer(), requestedAdaptive ? requested.getMaxAutomaticOffer() : null);
        boolean targetDefinitionChanged = targetDefinitionChanged(configuration, requested, requestedMode);
        boolean accountIdentityChanged = !sameNormalizedText(bot.getEmail(), normalizedEmail)
                || (request.getPassword() != null && !request.getPassword().isBlank());
        List<Listing> active = getActiveNegotiationListings(botId);
        List<Listing> mainActive = getMainProductActiveNegotiationListings(botId);
        validateActiveNegotiationEdit(
                bot, configuration, request, requested, requestedMode, requestedAdaptive,
                normalizedEmail, stepDefinitionChanged, active, mainActive);
        if (botRepository.existsByEmailAndIdNot(normalizedEmail, botId)) {
            throw new BotAlreadyExistsException(normalizedEmail);
        }
        bot.setName(normalizeRequiredText(request.getName()));
        bot.setEmail(normalizedEmail);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            bot.setPassword(request.getPassword());
        }
        configuration.setMarketplace(requested.getMarketplace());
        configuration.setCategoryPath(new ArrayList<>(requested.getCategoryPath()));
        configuration.setBrand(normalizeRequiredText(requested.getBrand()));
        configuration.setTargetMode(requestedMode);
        configuration.setModel(requestedMode == TargetMode.VINTED_MODEL ? normalizeRequiredText(requested.getModel()) : null);
        configuration.setSearchQuery(requestedMode == TargetMode.SEARCH_QUERY ? normalizeRequiredText(requested.getSearchQuery()) : null);
        configuration.setMinPrice(requested.getMinPrice());
        configuration.setMaxPrice(requested.getMaxPrice());
        configuration.setAutoRaiseOfferToVintedMinimum(requestedAdaptive);
        configuration.setMaxAutomaticOffer(requestedAdaptive ? requested.getMaxAutomaticOffer() : null);
        configuration.setDailyNegotiationBudget(requested.getDailyNegotiationBudget());
        if (stepDefinitionChanged) {
            replaceNegotiationSteps(configuration, requested.getNegotiationSteps());
        } else if (responsePoliciesChanged) {
            applyResponsePolicies(configuration, requested.getNegotiationSteps());
        }
        if (stepDefinitionChanged || adaptiveModeChanged || globalCapIncreased) {
            resetMainProductListingsWithStatus(botId, ListingStatus.SKIPPED_OFFER_TOO_LOW);
        }
        if (priceRangeChanged) {
            resetMainProductListingsWithStatus(botId, ListingStatus.SKIPPED_OUTSIDE_PRICE_RANGE);
        }
        if (targetDefinitionChanged) {
            resetMainProductListingsWithStatus(botId, ListingStatus.SKIPPED_TARGET_MISMATCH);
        }
        if (accountIdentityChanged) {
            resetAllListingsWithStatus(botId, ListingStatus.SKIPPED_CANNOT_NEGOTIATE);
        }
        return botMapper.map(bot);
    }

    @Transactional
    public void startBot(Long botId) {
        getBotEntity(botId).setStatus(BotStatus.RUNNING);
    }

    @Transactional
    public void stopBot(Long botId) {
        getBotEntity(botId).setStatus(BotStatus.STOPPED);
        // Do not depend on the scheduler observing STOPPED between STOP/START.
        sessionPreviewService.setPreviewRequested(botId, false);
    }

    public BotPlaywrightResponse getPlaywrightBot(Long botId) {
        Bot bot = getBotEntity(botId);
        if (bot.getStatus() != BotStatus.RUNNING) {
            throw new IllegalStateException("Bot is not running.");
        }
        return botMapper.mapPlaywright(bot);
    }

    public List<RunningBotResponse> getRunningBotIds() {
        return botRepository.findByStatus(BotStatus.RUNNING).stream().map(botMapper::mapRunning).toList();
    }

    private Bot getBotEntity(Long botId) {
        return botRepository.findById(botId).orElseThrow(() -> new BotNotFoundException(botId));
    }

    private List<Listing> getActiveNegotiationListings(Long botId) {
        List<Listing> result = new ArrayList<>(listingRepository.findByBotIdAndStatusOrderByIdAsc(botId, ListingStatus.NEGOTIATING));
        result.addAll(listingRepository.findByBotIdAndStatusOrderByIdAsc(botId, ListingStatus.ACTION_REQUIRED));
        return result;
    }

    private List<Listing> getMainProductActiveNegotiationListings(Long botId) {
        List<Listing> result = new ArrayList<>(
                listingRepository.findByBotIdAndStatusAndAdditionalTargetIsNullOrderByIdAsc(botId, ListingStatus.NEGOTIATING));
        result.addAll(listingRepository.findByBotIdAndStatusAndAdditionalTargetIsNullOrderByIdAsc(
                botId, ListingStatus.ACTION_REQUIRED));
        return result;
    }

    private BigDecimal minimumNegotiationCap(Bot bot) {
        BotConfiguration configuration = bot.getConfiguration();
        if (configuration == null || !Boolean.TRUE.equals(configuration.getAutoRaiseOfferToVintedMinimum())) {
            return null;
        }
        return configuration.getNegotiationSteps().stream()
                .filter(Objects::nonNull)
                .filter(step -> step.getStepNumber() != null)
                .min(Comparator.comparing(NegotiationStep::getStepNumber))
                .map(NegotiationStep::getOfferPrice)
                .orElse(null);
    }

    private void validateActiveNegotiationEdit(
            Bot bot,
            BotConfiguration configuration,
            UpdateBotRequest request,
            CreateBotConfigurationRequest requested,
            TargetMode requestedMode,
            boolean requestedAdaptive,
            String normalizedEmail,
            boolean stepDefinitionChanged,
            List<Listing> active,
            List<Listing> mainActive
    ) {
        if (active.isEmpty()) {
            return;
        }
        List<String> locked = new ArrayList<>();
        if (!sameNormalizedText(bot.getEmail(), normalizedEmail)) locked.add("Vinted e-mail");
        if (request.getPassword() != null && !request.getPassword().isBlank()) locked.add("Vinted password");
        if (!Objects.equals(configuration.getMarketplace(), requested.getMarketplace())) locked.add("marketplace");
        if (!mainActive.isEmpty()) {
            if (!Objects.equals(configuration.getCategoryPath(), requested.getCategoryPath())) locked.add("category");
            if (!sameNormalizedText(configuration.getBrand(), requested.getBrand())) locked.add("brand");
            TargetMode currentMode = configuration.getTargetMode() == null ? TargetMode.VINTED_MODEL : configuration.getTargetMode();
            if (currentMode != requestedMode) locked.add("target mode");
            if (requestedMode == TargetMode.VINTED_MODEL) {
                if (!sameNormalizedText(configuration.getModel(), requested.getModel())) locked.add("model");
            } else if (!sameNormalizedText(configuration.getSearchQuery(), requested.getSearchQuery())) {
                locked.add("search query");
            }
            if (Boolean.TRUE.equals(configuration.getAutoRaiseOfferToVintedMinimum()) != requestedAdaptive) {
                locked.add("adaptive pricing mode");
            }
            if (stepDefinitionChanged) locked.add("negotiation step prices/messages/structure");
        }
        if (!locked.isEmpty()) {
            throw new IllegalStateException(
                    "Bot has active negotiations. These fields cannot be changed while the affected conversations are active: "
                            + String.join(", ", locked)
                            + ". Shared Vinted account fields are locked by negotiations from any product; "
                            + "main-product target/strategy fields are locked only by main-product negotiations. "
                            + "Safe operational fields remain editable.");
        }
    }

    private boolean negotiationStepDefinitionChanged(BotConfiguration configuration, List<CreateNegotiationStepRequest> requested) {
        List<NegotiationStep> existing = orderedSteps(configuration);
        if (requested == null || existing.size() != requested.size()) return true;
        for (int i = 0; i < existing.size(); i++) {
            NegotiationStep left = existing.get(i);
            CreateNegotiationStepRequest right = requested.get(i);
            if (!Objects.equals(left.getStepNumber(), i + 1)
                    || !sameDecimal(left.getOfferPrice(), right.getOfferPrice())
                    || !sameDecimal(left.getMaxAcceptedCounterOffer(), right.getMaxAcceptedCounterOffer())
                    || !Objects.equals(left.getMessage(), right.getMessage())) return true;
        }
        return false;
    }

    private boolean negotiationResponsePoliciesChanged(BotConfiguration configuration, List<CreateNegotiationStepRequest> requested) {
        List<NegotiationStep> existing = orderedSteps(configuration);
        if (requested == null || existing.size() != requested.size()) return true;
        for (int i = 0; i < existing.size(); i++) {
            if (!samePolicy(existing.get(i), resolvePolicy(requested.get(i), i + 1))) return true;
        }
        return false;
    }

    private List<NegotiationStep> orderedSteps(BotConfiguration configuration) {
        return configuration.getNegotiationSteps().stream()
                .sorted(Comparator.comparing(step -> step.getStepNumber() == null ? Integer.MAX_VALUE : step.getStepNumber()))
                .toList();
    }

    private boolean samePolicy(NegotiationStep existing, ResolvedStepPolicy requested) {
        if (existing.getRejectionAction() != requested.rejectionAction()
                || !Objects.equals(existing.getRejectionWaitHours(), requested.rejectionWaitHours())
                || existing.getCounterOfferDefaultAction() != requested.counterDefaultAction()
                || !Objects.equals(existing.getCounterOfferDefaultWaitHours(), requested.counterDefaultWaitHours())) return false;
        List<CounterRuleValue> left = existing.getCounterOfferRules().stream()
                .map(rule -> new CounterRuleValue(rule.getMinimumDiscountPercent(), rule.getAction(), rule.getWaitHours()))
                .sorted(Comparator.comparing(CounterRuleValue::minimumDiscountPercent)).toList();
        List<CounterRuleValue> right = requested.rules().stream()
                .sorted(Comparator.comparing(CounterRuleValue::minimumDiscountPercent)).toList();
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            CounterRuleValue a = left.get(i);
            CounterRuleValue b = right.get(i);
            if (!sameDecimal(a.minimumDiscountPercent(), b.minimumDiscountPercent())
                    || a.action() != b.action()
                    || !Objects.equals(a.waitHours(), b.waitHours())) return false;
        }
        return true;
    }

    private boolean targetDefinitionChanged(BotConfiguration current, CreateBotConfigurationRequest requested, TargetMode requestedMode) {
        if (!Objects.equals(current.getMarketplace(), requested.getMarketplace())
                || !Objects.equals(current.getCategoryPath(), requested.getCategoryPath())
                || !sameNormalizedText(current.getBrand(), requested.getBrand())) return true;
        TargetMode currentMode = current.getTargetMode() == null ? TargetMode.VINTED_MODEL : current.getTargetMode();
        if (currentMode != requestedMode) return true;
        return requestedMode == TargetMode.VINTED_MODEL
                ? !sameNormalizedText(current.getModel(), requested.getModel())
                : !sameNormalizedText(current.getSearchQuery(), requested.getSearchQuery());
    }

    private boolean isGlobalCapIncreased(BigDecimal currentCap, BigDecimal requestedCap) {
        return requestedCap != null && (currentCap == null || requestedCap.compareTo(currentCap) > 0);
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        return left == null || right == null ? left == right : left.compareTo(right) == 0;
    }

    private boolean sameNormalizedText(String left, String right) {
        return left == null || right == null ? left == right : normalizeRequiredText(left).equals(normalizeRequiredText(right));
    }

    private void resetMainProductListingsWithStatus(Long botId, ListingStatus status) {
        for (Listing listing : listingRepository.findByBotIdAndStatusAndAdditionalTargetIsNullOrderByIdAsc(botId, status)) {
            listing.setStatus(ListingStatus.DISCOVERED);
        }
    }

    private void resetAllListingsWithStatus(Long botId, ListingStatus status) {
        for (Listing listing : listingRepository.findByBotIdAndStatusOrderByIdAsc(botId, status)) {
            listing.setStatus(ListingStatus.DISCOVERED);
        }
    }

    private void replaceNegotiationSteps(BotConfiguration configuration, List<CreateNegotiationStepRequest> requests) {
        configuration.getNegotiationSteps().clear();
        for (int i = 0; i < requests.size(); i++) {
            CreateNegotiationStepRequest request = requests.get(i);
            int stepNumber = i + 1;
            ResolvedStepPolicy policy = resolvePolicy(request, stepNumber);
            configuration.getNegotiationSteps().add(NegotiationStep.builder()
                    .stepNumber(stepNumber)
                    .offerPrice(request.getOfferPrice())
                    .maxAcceptedCounterOffer(request.getMaxAcceptedCounterOffer())
                    .message(request.getMessage())
                    .rejectionAction(policy.rejectionAction())
                    .rejectionWaitHours(policy.rejectionWaitHours())
                    .counterOfferDefaultAction(policy.counterDefaultAction())
                    .counterOfferDefaultWaitHours(policy.counterDefaultWaitHours())
                    .counterOfferRules(toRuleEntities(policy.rules()))
                    .configuration(configuration)
                    .build());
        }
    }

    private void applyResponsePolicies(BotConfiguration configuration, List<CreateNegotiationStepRequest> requests) {
        List<NegotiationStep> existing = orderedSteps(configuration);
        if (existing.size() != requests.size()) {
            throw new IllegalStateException("Cannot apply response policies because negotiation step structure changed.");
        }
        for (int i = 0; i < existing.size(); i++) {
            NegotiationStep step = existing.get(i);
            ResolvedStepPolicy policy = resolvePolicy(requests.get(i), i + 1);
            step.setRejectionAction(policy.rejectionAction());
            step.setRejectionWaitHours(policy.rejectionWaitHours());
            step.setCounterOfferDefaultAction(policy.counterDefaultAction());
            step.setCounterOfferDefaultWaitHours(policy.counterDefaultWaitHours());
            step.getCounterOfferRules().clear();
            step.getCounterOfferRules().addAll(toRuleEntities(policy.rules()));
        }
    }

    private List<SellerCounterOfferRule> toRuleEntities(List<CounterRuleValue> rules) {
        return rules.stream()
                .sorted(Comparator.comparing(CounterRuleValue::minimumDiscountPercent))
                .map(rule -> SellerCounterOfferRule.builder()
                        .minimumDiscountPercent(rule.minimumDiscountPercent())
                        .action(rule.action()).waitHours(rule.waitHours()).build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private void validateConfiguration(CreateBotConfigurationRequest request) {
        TargetMode mode = resolveTargetMode(request);
        validatePriceRange(request.getMinPrice(), request.getMaxPrice());
        if (request.getDailyNegotiationBudget() == null || request.getDailyNegotiationBudget() <= 0) {
            throw new IllegalArgumentException("Daily negotiation budget must be greater than 0.");
        }
        if (request.getNegotiationSteps() == null || request.getNegotiationSteps().isEmpty()) {
            throw new IllegalArgumentException("At least one negotiation step is required.");
        }
        if (mode == TargetMode.VINTED_MODEL) {
            requireNonBlank(request.getModel(), "Model is required for target mode VINTED_MODEL.");
        } else if (mode == TargetMode.SEARCH_QUERY) {
            requireNonBlank(request.getSearchQuery(), "Search query is required for target mode SEARCH_QUERY.");
        }
        if (Boolean.TRUE.equals(request.getAutoRaiseOfferToVintedMinimum())) {
            BigDecimal cap = request.getMaxAutomaticOffer();
            if (cap == null || cap.signum() <= 0) {
                throw new IllegalArgumentException("Max automatic offer must be greater than 0 when adaptive pricing is enabled.");
            }
            if (request.getMaxPrice() != null && cap.compareTo(request.getMaxPrice()) > 0) {
                throw new IllegalArgumentException("Max automatic offer cannot be greater than the configured maximum listing price.");
            }
        }
        for (int i = 0; i < request.getNegotiationSteps().size(); i++) {
            validateResolvedPolicy(resolvePolicy(request.getNegotiationSteps().get(i), i + 1), i + 1);
        }
    }

    private void validateResolvedPolicy(ResolvedStepPolicy policy, int stepNumber) {
        validateReaction(policy.rejectionAction(), policy.rejectionWaitHours(), "Step " + stepNumber + " rejection policy");
        validateReaction(policy.counterDefaultAction(), policy.counterDefaultWaitHours(), "Step " + stepNumber + " counteroffer fallback");
        Set<String> thresholds = new HashSet<>();
        for (CounterRuleValue rule : policy.rules()) {
            if (rule.minimumDiscountPercent() == null
                    || rule.minimumDiscountPercent().signum() <= 0
                    || rule.minimumDiscountPercent().compareTo(MAX_DISCOUNT_PERCENT) > 0) {
                throw new IllegalArgumentException("Step " + stepNumber + " counteroffer discount threshold must be greater than 0 and at most 100%.");
            }
            String threshold = rule.minimumDiscountPercent().stripTrailingZeros().toPlainString();
            if (!thresholds.add(threshold)) {
                throw new IllegalArgumentException("Step " + stepNumber + " contains duplicate counteroffer discount threshold " + threshold + "%.");
            }
            validateReaction(rule.action(), rule.waitHours(), "Step " + stepNumber + " counteroffer rule " + threshold + "%");
        }
    }

    private void validateReaction(NegotiationReactionAction action, Integer waitHours, String label) {
        if (action == null) throw new IllegalArgumentException(label + " has no action.");
        if (action == NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
                && (waitHours == null || waitHours < 1 || waitHours > MAX_RESPONSE_WAIT_HOURS)) {
            throw new IllegalArgumentException(label + " wait time must be between 1 and " + MAX_RESPONSE_WAIT_HOURS + " hours.");
        }
    }

    private ResolvedStepPolicy resolvePolicy(CreateNegotiationStepRequest request, int stepNumber) {
        NegotiationReactionAction rejection = request.getRejectionAction();
        Integer rejectionWait = request.getRejectionWaitHours();
        if (rejection == null) {
            if (stepNumber == 1) {
                rejection = NegotiationReactionAction.NEXT_STEP_NOW;
                rejectionWait = null;
            } else {
                rejection = NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP;
                rejectionWait = defaultRejectionWaitHours(stepNumber);
            }
        }
        if (rejection == NegotiationReactionAction.NEXT_STEP_NOW) rejectionWait = null;
        NegotiationReactionAction counterDefault = request.getCounterOfferDefaultAction();
        Integer counterWait = request.getCounterOfferDefaultWaitHours();
        if (counterDefault == null) {
            counterDefault = NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP;
            counterWait = 6;
        }
        if (counterDefault == NegotiationReactionAction.NEXT_STEP_NOW) counterWait = null;
        List<CounterRuleValue> rules = request.getCounterOfferRules() == null
                ? defaultCounterOfferRules()
                : request.getCounterOfferRules().stream().filter(Objects::nonNull).map(this::toRuleValue).toList();
        return new ResolvedStepPolicy(rejection, rejectionWait, counterDefault, counterWait, rules);
    }

    private CounterRuleValue toRuleValue(SellerCounterOfferRuleRequest request) {
        NegotiationReactionAction action = request.getAction();
        Integer wait = request.getWaitHours();
        if (action == NegotiationReactionAction.NEXT_STEP_NOW) wait = null;
        return new CounterRuleValue(request.getMinimumDiscountPercent(), action, wait);
    }

    private int defaultRejectionWaitHours(int stepNumber) {
        if (stepNumber == 2) return 6;
        if (stepNumber == 3) return 12;
        return 24;
    }

    private List<CounterRuleValue> defaultCounterOfferRules() {
        return List.of(
                new CounterRuleValue(new BigDecimal("10"), NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP, 2),
                new CounterRuleValue(new BigDecimal("15"), NegotiationReactionAction.NEXT_STEP_NOW, null));
    }

    private TargetMode resolveTargetMode(CreateBotConfigurationRequest request) {
        return request.getTargetMode() == null ? TargetMode.VINTED_MODEL : request.getTargetMode();
    }

    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice == null || maxPrice == null) return;
        if (minPrice.signum() < 0 || maxPrice.signum() < 0) throw new IllegalArgumentException("Listing prices cannot be negative.");
        if (minPrice.compareTo(maxPrice) > 0) throw new IllegalArgumentException("Minimum listing price cannot be greater than maximum listing price.");
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
    }

    private String normalizeRequiredText(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private record ResolvedStepPolicy(
            NegotiationReactionAction rejectionAction,
            Integer rejectionWaitHours,
            NegotiationReactionAction counterDefaultAction,
            Integer counterDefaultWaitHours,
            List<CounterRuleValue> rules
    ) {}

    private record CounterRuleValue(BigDecimal minimumDiscountPercent, NegotiationReactionAction action, Integer waitHours) {}
}

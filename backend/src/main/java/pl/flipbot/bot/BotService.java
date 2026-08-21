package pl.flipbot.bot;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.bot.dto.BotEditCapabilitiesResponse;
import pl.flipbot.bot.dto.BotPlaywrightResponse;
import pl.flipbot.bot.dto.BotResponse;
import pl.flipbot.bot.dto.CreateBotConfigurationRequest;
import pl.flipbot.bot.dto.CreateBotRequest;
import pl.flipbot.bot.dto.RunningBotResponse;
import pl.flipbot.bot.dto.UpdateBotRequest;
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
    private static final int DEFAULT_READ_WAIT_HOURS = 3;
    private static final int DEFAULT_UNREAD_WAIT_HOURS = 48;
    private static final BigDecimal MAX_DISCOUNT_PERCENT = new BigDecimal("100");

    private final BotRepository botRepository;
    private final BotConfigurationRepository botConfigurationRepository;
    private final ListingRepository listingRepository;
    private final BotMapper botMapper;

    public List<BotResponse> getAllBots() {
        return botRepository.findAll().stream().map(botMapper::map).toList();
    }

    public BotResponse getBot(Long botId) {
        return botMapper.map(getBotEntity(botId));
    }

    public BotEditCapabilitiesResponse getEditCapabilities(Long botId) {
        Bot bot = getBotEntity(botId);
        List<Listing> activeListings = getActiveNegotiationListings(botId);

        return BotEditCapabilitiesResponse.builder()
                .hasActiveNegotiations(!activeListings.isEmpty())
                .minimumNegotiationCap(minimumNegotiationCap(bot))
                .build();
    }

    @Transactional
    public BotResponse createBot(CreateBotRequest request) {
        if (botRepository.existsByEmail(request.getEmail())) {
            throw new BotAlreadyExistsException(request.getEmail());
        }

        CreateBotConfigurationRequest configurationRequest = request.getConfiguration();
        validateConfiguration(configurationRequest);

        TargetMode targetMode = resolveTargetMode(configurationRequest);
        boolean adaptiveMode = Boolean.TRUE.equals(
                configurationRequest.getAutoRaiseOfferToVintedMinimum()
        );

        Bot bot = Bot.builder()
                .name(normalizeRequiredText(request.getName()))
                .email(normalizeRequiredText(request.getEmail()))
                .password(request.getPassword())
                .status(BotStatus.STOPPED)
                .build();

        Bot savedBot = botRepository.save(bot);

        BotConfiguration configuration = BotConfiguration.builder()
                .marketplace(configurationRequest.getMarketplace())
                .categoryPath(new ArrayList<>(configurationRequest.getCategoryPath()))
                .brand(normalizeRequiredText(configurationRequest.getBrand()))
                .targetMode(targetMode)
                .model(targetMode == TargetMode.VINTED_MODEL
                        ? normalizeRequiredText(configurationRequest.getModel())
                        : null)
                .searchQuery(targetMode == TargetMode.SEARCH_QUERY
                        ? normalizeRequiredText(configurationRequest.getSearchQuery())
                        : null)
                .minPrice(configurationRequest.getMinPrice())
                .maxPrice(configurationRequest.getMaxPrice())
                .autoRaiseOfferToVintedMinimum(adaptiveMode)
                .maxAutomaticOffer(adaptiveMode
                        ? configurationRequest.getMaxAutomaticOffer()
                        : null)
                .dailyNegotiationBudget(configurationRequest.getDailyNegotiationBudget())
                .bot(savedBot)
                .build();

        savedBot.setConfiguration(configuration);
        replaceNegotiationSteps(configuration, configurationRequest.getNegotiationSteps());
        botConfigurationRepository.save(configuration);

        return botMapper.map(savedBot);
    }

    @Transactional
    public BotResponse updateBot(Long botId, UpdateBotRequest request) {
        Bot bot = getBotEntity(botId);

        if (bot.getStatus() != BotStatus.STOPPED) {
            throw new IllegalStateException(
                    "Only a stopped bot can be edited. Stop the bot first."
            );
        }

        BotConfiguration configuration = bot.getConfiguration();
        if (configuration == null) {
            throw new IllegalStateException("Bot configuration does not exist.");
        }

        CreateBotConfigurationRequest requestedConfiguration = request.getConfiguration();
        validateConfiguration(requestedConfiguration);

        TargetMode requestedTargetMode = resolveTargetMode(requestedConfiguration);
        boolean requestedAdaptiveMode = Boolean.TRUE.equals(
                requestedConfiguration.getAutoRaiseOfferToVintedMinimum()
        );
        String normalizedEmail = normalizeRequiredText(request.getEmail());

        boolean stepDefinitionChanged = negotiationStepDefinitionChanged(
                configuration,
                requestedConfiguration.getNegotiationSteps()
        );
        boolean responsePoliciesChanged = negotiationResponsePoliciesChanged(
                configuration,
                requestedConfiguration.getNegotiationSteps()
        );
        boolean priceRangeChanged = !sameDecimal(
                configuration.getMinPrice(),
                requestedConfiguration.getMinPrice()
        ) || !sameDecimal(
                configuration.getMaxPrice(),
                requestedConfiguration.getMaxPrice()
        );
        boolean adaptiveModeChanged = Boolean.TRUE.equals(
                configuration.getAutoRaiseOfferToVintedMinimum()
        ) != requestedAdaptiveMode;
        boolean globalCapIncreased = isGlobalCapIncreased(
                configuration.getMaxAutomaticOffer(),
                requestedAdaptiveMode
                        ? requestedConfiguration.getMaxAutomaticOffer()
                        : null
        );
        boolean targetDefinitionChanged = targetDefinitionChanged(
                configuration,
                requestedConfiguration,
                requestedTargetMode
        );
        boolean accountIdentityChanged = !sameNormalizedText(
                bot.getEmail(),
                normalizedEmail
        ) || (request.getPassword() != null && !request.getPassword().isBlank());

        List<Listing> activeListings = getActiveNegotiationListings(botId);

        validateActiveNegotiationEdit(
                bot,
                configuration,
                request,
                requestedConfiguration,
                requestedTargetMode,
                requestedAdaptiveMode,
                normalizedEmail,
                stepDefinitionChanged,
                activeListings
        );

        if (botRepository.existsByEmailAndIdNot(normalizedEmail, botId)) {
            throw new BotAlreadyExistsException(normalizedEmail);
        }

        bot.setName(normalizeRequiredText(request.getName()));
        bot.setEmail(normalizedEmail);
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            bot.setPassword(request.getPassword());
        }

        configuration.setMarketplace(requestedConfiguration.getMarketplace());
        configuration.setCategoryPath(new ArrayList<>(requestedConfiguration.getCategoryPath()));
        configuration.setBrand(normalizeRequiredText(requestedConfiguration.getBrand()));
        configuration.setTargetMode(requestedTargetMode);
        configuration.setModel(requestedTargetMode == TargetMode.VINTED_MODEL
                ? normalizeRequiredText(requestedConfiguration.getModel())
                : null);
        configuration.setSearchQuery(requestedTargetMode == TargetMode.SEARCH_QUERY
                ? normalizeRequiredText(requestedConfiguration.getSearchQuery())
                : null);
        configuration.setMinPrice(requestedConfiguration.getMinPrice());
        configuration.setMaxPrice(requestedConfiguration.getMaxPrice());
        configuration.setAutoRaiseOfferToVintedMinimum(requestedAdaptiveMode);
        configuration.setMaxAutomaticOffer(requestedAdaptiveMode
                ? requestedConfiguration.getMaxAutomaticOffer()
                : null);
        configuration.setDailyNegotiationBudget(requestedConfiguration.getDailyNegotiationBudget());

        if (stepDefinitionChanged) {
            replaceNegotiationSteps(
                    configuration,
                    requestedConfiguration.getNegotiationSteps()
            );
        } else if (responsePoliciesChanged) {
            /*
             * Policy-only edits are safe during active negotiations because
             * they do not change what currentStep means. Update the existing
             * step entities in place instead of recreating them.
             */
            applyResponsePolicies(
                    configuration,
                    requestedConfiguration.getNegotiationSteps()
            );
        }

        if (stepDefinitionChanged || adaptiveModeChanged || globalCapIncreased) {
            resetSkippedOfferTooLowListings(botId);
        }

        if (priceRangeChanged) {
            resetSkippedOutsidePriceRangeListings(botId);
        }

        /*
         * A target mismatch is only valid for the target that classified it.
         * If category/brand/model/query/marketplace changes, historical
         * mismatches must become DISCOVERED so the new target can evaluate
         * them again instead of inheriting stale decisions forever.
         */
        if (targetDefinitionChanged) {
            resetSkippedTargetMismatchListings(botId);
        }

        /*
         * CANNOT_NEGOTIATE can be account-specific (permissions, seller block,
         * account state). A new Vinted identity deserves one fresh check.
         */
        if (accountIdentityChanged) {
            resetSkippedCannotNegotiateListings(botId);
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
    }

    public BotPlaywrightResponse getPlaywrightBot(Long botId) {
        Bot bot = getBotEntity(botId);
        if (bot.getStatus() != BotStatus.RUNNING) {
            throw new IllegalStateException("Bot is not running.");
        }
        return botMapper.mapPlaywright(bot);
    }

    public List<RunningBotResponse> getRunningBotIds() {
        return botRepository.findByStatus(BotStatus.RUNNING)
                .stream()
                .map(botMapper::mapRunning)
                .toList();
    }

    private Bot getBotEntity(Long botId) {
        return botRepository.findById(botId)
                .orElseThrow(() -> new BotNotFoundException(botId));
    }

    private List<Listing> getActiveNegotiationListings(Long botId) {
        List<Listing> activeListings = new ArrayList<>(
                listingRepository.findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        ListingStatus.NEGOTIATING
                )
        );
        activeListings.addAll(
                listingRepository.findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        ListingStatus.ACTION_REQUIRED
                )
        );
        return activeListings;
    }

    private BigDecimal minimumNegotiationCap(Bot bot) {
        BotConfiguration configuration = bot.getConfiguration();
        if (configuration == null
                || !Boolean.TRUE.equals(configuration.getAutoRaiseOfferToVintedMinimum())) {
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
            CreateBotConfigurationRequest requestedConfiguration,
            TargetMode requestedTargetMode,
            boolean requestedAdaptiveMode,
            String normalizedEmail,
            boolean stepDefinitionChanged,
            List<Listing> activeListings
    ) {
        if (activeListings.isEmpty()) {
            return;
        }

        List<String> lockedChanges = new ArrayList<>();

        if (!sameNormalizedText(bot.getEmail(), normalizedEmail)) {
            lockedChanges.add("Vinted e-mail");
        }
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            lockedChanges.add("Vinted password");
        }
        if (!Objects.equals(configuration.getMarketplace(), requestedConfiguration.getMarketplace())) {
            lockedChanges.add("marketplace");
        }
        if (!Objects.equals(configuration.getCategoryPath(), requestedConfiguration.getCategoryPath())) {
            lockedChanges.add("category");
        }
        if (!sameNormalizedText(configuration.getBrand(), requestedConfiguration.getBrand())) {
            lockedChanges.add("brand");
        }

        TargetMode currentTargetMode = configuration.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : configuration.getTargetMode();
        if (currentTargetMode != requestedTargetMode) {
            lockedChanges.add("target mode");
        }

        if (requestedTargetMode == TargetMode.VINTED_MODEL) {
            if (!sameNormalizedText(configuration.getModel(), requestedConfiguration.getModel())) {
                lockedChanges.add("model");
            }
        } else if (!sameNormalizedText(
                configuration.getSearchQuery(),
                requestedConfiguration.getSearchQuery()
        )) {
            lockedChanges.add("search query");
        }

        boolean currentAdaptiveMode = Boolean.TRUE.equals(
                configuration.getAutoRaiseOfferToVintedMinimum()
        );
        if (currentAdaptiveMode != requestedAdaptiveMode) {
            lockedChanges.add("adaptive pricing mode");
        }

        if (stepDefinitionChanged) {
            lockedChanges.add("negotiation step prices/messages/structure");
        }

        if (!lockedChanges.isEmpty()) {
            throw new IllegalStateException(
                    "Bot has active negotiations. These fields cannot be changed until all active negotiations are finished: "
                            + String.join(", ", lockedChanges)
                            + ". Allowed while active: bot name, listing min/max price, daily negotiation budget, "
                            + "global negotiation cap and per-step rejection/counteroffer/read/unread response policies."
            );
        }
    }

    private boolean negotiationStepDefinitionChanged(
            BotConfiguration configuration,
            List<CreateNegotiationStepRequest> requestedSteps
    ) {
        List<NegotiationStep> existingSteps = orderedSteps(configuration);
        if (requestedSteps == null || existingSteps.size() != requestedSteps.size()) {
            return true;
        }

        for (int index = 0; index < existingSteps.size(); index++) {
            NegotiationStep existing = existingSteps.get(index);
            CreateNegotiationStepRequest requested = requestedSteps.get(index);

            if (!Objects.equals(existing.getStepNumber(), index + 1)
                    || !sameDecimal(existing.getOfferPrice(), requested.getOfferPrice())
                    || !sameDecimal(
                    existing.getMaxAcceptedCounterOffer(),
                    requested.getMaxAcceptedCounterOffer()
            )
                    || !Objects.equals(existing.getMessage(), requested.getMessage())) {
                return true;
            }
        }

        return false;
    }

    private boolean negotiationResponsePoliciesChanged(
            BotConfiguration configuration,
            List<CreateNegotiationStepRequest> requestedSteps
    ) {
        List<NegotiationStep> existingSteps = orderedSteps(configuration);
        if (requestedSteps == null || existingSteps.size() != requestedSteps.size()) {
            return true;
        }

        for (int index = 0; index < existingSteps.size(); index++) {
            if (!samePolicy(
                    existingSteps.get(index),
                    resolvePolicy(requestedSteps.get(index), index + 1)
            )) {
                return true;
            }
        }
        return false;
    }

    private List<NegotiationStep> orderedSteps(BotConfiguration configuration) {
        return configuration.getNegotiationSteps().stream()
                .sorted(Comparator.comparing(
                        step -> step.getStepNumber() == null
                                ? Integer.MAX_VALUE
                                : step.getStepNumber()
                ))
                .toList();
    }

    private boolean samePolicy(NegotiationStep existing, ResolvedStepPolicy requested) {
        if (existing.getRejectionAction() != requested.rejectionAction()
                || !Objects.equals(existing.getRejectionWaitHours(), requested.rejectionWaitHours())
                || !Objects.equals(existing.getReadWaitHours(), requested.readWaitHours())
                || !Objects.equals(existing.getUnreadWaitHours(), requested.unreadWaitHours())
                || existing.getCounterOfferDefaultAction() != requested.counterDefaultAction()
                || !Objects.equals(
                existing.getCounterOfferDefaultWaitHours(),
                requested.counterDefaultWaitHours()
        )) {
            return false;
        }

        List<CounterRuleValue> existingRules = existing.getCounterOfferRules().stream()
                .map(rule -> new CounterRuleValue(
                        rule.getMinimumDiscountPercent(),
                        rule.getAction(),
                        rule.getWaitHours()
                ))
                .sorted(Comparator.comparing(CounterRuleValue::minimumDiscountPercent))
                .toList();

        List<CounterRuleValue> requestedRules = requested.rules().stream()
                .sorted(Comparator.comparing(CounterRuleValue::minimumDiscountPercent))
                .toList();

        if (existingRules.size() != requestedRules.size()) {
            return false;
        }

        for (int index = 0; index < existingRules.size(); index++) {
            CounterRuleValue left = existingRules.get(index);
            CounterRuleValue right = requestedRules.get(index);
            if (!sameDecimal(left.minimumDiscountPercent(), right.minimumDiscountPercent())
                    || left.action() != right.action()
                    || !Objects.equals(left.waitHours(), right.waitHours())) {
                return false;
            }
        }
        return true;
    }

    private boolean targetDefinitionChanged(
            BotConfiguration current,
            CreateBotConfigurationRequest requested,
            TargetMode requestedTargetMode
    ) {
        if (!Objects.equals(current.getMarketplace(), requested.getMarketplace())
                || !Objects.equals(current.getCategoryPath(), requested.getCategoryPath())
                || !sameNormalizedText(current.getBrand(), requested.getBrand())) {
            return true;
        }

        TargetMode currentTargetMode = current.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : current.getTargetMode();

        if (currentTargetMode != requestedTargetMode) {
            return true;
        }

        if (requestedTargetMode == TargetMode.VINTED_MODEL) {
            return !sameNormalizedText(current.getModel(), requested.getModel());
        }

        return !sameNormalizedText(current.getSearchQuery(), requested.getSearchQuery());
    }

    private boolean isGlobalCapIncreased(BigDecimal currentCap, BigDecimal requestedCap) {
        if (requestedCap == null) {
            return false;
        }
        return currentCap == null || requestedCap.compareTo(currentCap) > 0;
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private boolean sameNormalizedText(String left, String right) {
        if (left == null || right == null) {
            return left == right;
        }
        return normalizeRequiredText(left).equals(normalizeRequiredText(right));
    }

    private void resetSkippedOfferTooLowListings(Long botId) {
        resetListingsWithStatus(
                botId,
                ListingStatus.SKIPPED_OFFER_TOO_LOW
        );
    }

    private void resetSkippedOutsidePriceRangeListings(Long botId) {
        resetListingsWithStatus(
                botId,
                ListingStatus.SKIPPED_OUTSIDE_PRICE_RANGE
        );
    }

    private void resetSkippedTargetMismatchListings(Long botId) {
        resetListingsWithStatus(
                botId,
                ListingStatus.SKIPPED_TARGET_MISMATCH
        );
    }

    private void resetSkippedCannotNegotiateListings(Long botId) {
        resetListingsWithStatus(
                botId,
                ListingStatus.SKIPPED_CANNOT_NEGOTIATE
        );
    }

    private void resetListingsWithStatus(
            Long botId,
            ListingStatus status
    ) {
        for (Listing listing : listingRepository.findByBotIdAndStatusOrderByIdAsc(
                botId,
                status
        )) {
            listing.setStatus(ListingStatus.DISCOVERED);
        }
    }

    private void replaceNegotiationSteps(
            BotConfiguration configuration,
            List<CreateNegotiationStepRequest> stepRequests
    ) {
        configuration.getNegotiationSteps().clear();

        for (int index = 0; index < stepRequests.size(); index++) {
            CreateNegotiationStepRequest request = stepRequests.get(index);
            int stepNumber = index + 1;
            ResolvedStepPolicy policy = resolvePolicy(request, stepNumber);

            NegotiationStep step = NegotiationStep.builder()
                    .stepNumber(stepNumber)
                    .offerPrice(request.getOfferPrice())
                    .maxAcceptedCounterOffer(request.getMaxAcceptedCounterOffer())
                    .message(request.getMessage())
                    .rejectionAction(policy.rejectionAction())
                    .rejectionWaitHours(policy.rejectionWaitHours())
                    .readWaitHours(policy.readWaitHours())
                    .unreadWaitHours(policy.unreadWaitHours())
                    .counterOfferDefaultAction(policy.counterDefaultAction())
                    .counterOfferDefaultWaitHours(policy.counterDefaultWaitHours())
                    .counterOfferRules(toRuleEntities(policy.rules()))
                    .configuration(configuration)
                    .build();

            configuration.getNegotiationSteps().add(step);
        }
    }

    private void applyResponsePolicies(
            BotConfiguration configuration,
            List<CreateNegotiationStepRequest> stepRequests
    ) {
        List<NegotiationStep> existingSteps = orderedSteps(configuration);
        if (existingSteps.size() != stepRequests.size()) {
            throw new IllegalStateException(
                    "Cannot apply response policies because negotiation step structure changed."
            );
        }

        for (int index = 0; index < existingSteps.size(); index++) {
            NegotiationStep step = existingSteps.get(index);
            ResolvedStepPolicy policy = resolvePolicy(stepRequests.get(index), index + 1);

            step.setRejectionAction(policy.rejectionAction());
            step.setRejectionWaitHours(policy.rejectionWaitHours());
            step.setReadWaitHours(policy.readWaitHours());
            step.setUnreadWaitHours(policy.unreadWaitHours());
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
                        .action(rule.action())
                        .waitHours(rule.waitHours())
                        .build())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private void validateConfiguration(CreateBotConfigurationRequest request) {
        TargetMode targetMode = resolveTargetMode(request);
        validatePriceRange(request.getMinPrice(), request.getMaxPrice());

        if (request.getDailyNegotiationBudget() == null
                || request.getDailyNegotiationBudget() <= 0) {
            throw new IllegalArgumentException(
                    "Daily negotiation budget must be greater than 0."
            );
        }

        if (request.getNegotiationSteps() == null
                || request.getNegotiationSteps().isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one negotiation step is required."
            );
        }

        if (targetMode == TargetMode.VINTED_MODEL) {
            requireNonBlank(
                    request.getModel(),
                    "Model is required for target mode VINTED_MODEL."
            );
        } else if (targetMode == TargetMode.SEARCH_QUERY) {
            requireNonBlank(
                    request.getSearchQuery(),
                    "Search query is required for target mode SEARCH_QUERY."
            );
        }

        boolean adaptiveMode = Boolean.TRUE.equals(
                request.getAutoRaiseOfferToVintedMinimum()
        );
        if (adaptiveMode) {
            BigDecimal maxAutomaticOffer = request.getMaxAutomaticOffer();
            if (maxAutomaticOffer == null || maxAutomaticOffer.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Max automatic offer must be greater than 0 when adaptive pricing is enabled."
                );
            }
            if (request.getMaxPrice() != null
                    && maxAutomaticOffer.compareTo(request.getMaxPrice()) > 0) {
                throw new IllegalArgumentException(
                        "Max automatic offer cannot be greater than the configured maximum listing price."
                );
            }
        }

        for (int index = 0; index < request.getNegotiationSteps().size(); index++) {
            validateResolvedPolicy(
                    resolvePolicy(request.getNegotiationSteps().get(index), index + 1),
                    index + 1
            );
        }
    }

    private void validateResolvedPolicy(ResolvedStepPolicy policy, int stepNumber) {
        validateReaction(
                policy.rejectionAction(),
                policy.rejectionWaitHours(),
                "Step " + stepNumber + " rejection policy"
        );
        validatePendingWaitHours(
                policy.readWaitHours(),
                "Step " + stepNumber + " read follow-up"
        );
        validatePendingWaitHours(
                policy.unreadWaitHours(),
                "Step " + stepNumber + " unread follow-up"
        );
        validateReaction(
                policy.counterDefaultAction(),
                policy.counterDefaultWaitHours(),
                "Step " + stepNumber + " counteroffer fallback"
        );

        Set<String> thresholds = new HashSet<>();
        for (CounterRuleValue rule : policy.rules()) {
            if (rule.minimumDiscountPercent() == null
                    || rule.minimumDiscountPercent().signum() <= 0
                    || rule.minimumDiscountPercent().compareTo(MAX_DISCOUNT_PERCENT) > 0) {
                throw new IllegalArgumentException(
                        "Step " + stepNumber
                                + " counteroffer discount threshold must be greater than 0 and at most 100%."
                );
            }

            String normalizedThreshold = rule.minimumDiscountPercent()
                    .stripTrailingZeros()
                    .toPlainString();
            if (!thresholds.add(normalizedThreshold)) {
                throw new IllegalArgumentException(
                        "Step " + stepNumber
                                + " contains duplicate counteroffer discount threshold "
                                + normalizedThreshold + "%."
                );
            }

            validateReaction(
                    rule.action(),
                    rule.waitHours(),
                    "Step " + stepNumber + " counteroffer rule " + normalizedThreshold + "%"
            );
        }
    }

    private void validateReaction(
            NegotiationReactionAction action,
            Integer waitHours,
            String label
    ) {
        if (action == null) {
            throw new IllegalArgumentException(label + " has no action.");
        }

        if (action == NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
                && (waitHours == null
                || waitHours < 1
                || waitHours > MAX_RESPONSE_WAIT_HOURS)) {
            throw new IllegalArgumentException(
                    label + " wait time must be between 1 and "
                            + MAX_RESPONSE_WAIT_HOURS + " hours."
            );
        }
    }

    private void validatePendingWaitHours(Integer waitHours, String label) {
        if (waitHours == null
                || waitHours < 1
                || waitHours > MAX_RESPONSE_WAIT_HOURS) {
            throw new IllegalArgumentException(
                    label + " wait time must be between 1 and "
                            + MAX_RESPONSE_WAIT_HOURS + " hours."
            );
        }
    }

    private ResolvedStepPolicy resolvePolicy(
            CreateNegotiationStepRequest request,
            int stepNumber
    ) {
        NegotiationReactionAction rejectionAction = request.getRejectionAction();
        Integer rejectionWaitHours = request.getRejectionWaitHours();

        if (rejectionAction == null) {
            if (stepNumber == 1) {
                rejectionAction = NegotiationReactionAction.NEXT_STEP_NOW;
                rejectionWaitHours = null;
            } else {
                rejectionAction = NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP;
                rejectionWaitHours = defaultRejectionWaitHours(stepNumber);
            }
        }
        if (rejectionAction == NegotiationReactionAction.NEXT_STEP_NOW) {
            rejectionWaitHours = null;
        }

        Integer readWaitHours = request.getReadWaitHours() == null
                ? DEFAULT_READ_WAIT_HOURS
                : request.getReadWaitHours();
        Integer unreadWaitHours = request.getUnreadWaitHours() == null
                ? DEFAULT_UNREAD_WAIT_HOURS
                : request.getUnreadWaitHours();

        NegotiationReactionAction counterDefaultAction =
                request.getCounterOfferDefaultAction();
        Integer counterDefaultWaitHours = request.getCounterOfferDefaultWaitHours();
        if (counterDefaultAction == null) {
            counterDefaultAction = NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP;
            counterDefaultWaitHours = 6;
        }
        if (counterDefaultAction == NegotiationReactionAction.NEXT_STEP_NOW) {
            counterDefaultWaitHours = null;
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
                rejectionWaitHours,
                readWaitHours,
                unreadWaitHours,
                counterDefaultAction,
                counterDefaultWaitHours,
                rules
        );
    }

    private CounterRuleValue toRuleValue(SellerCounterOfferRuleRequest request) {
        NegotiationReactionAction action = request.getAction();
        Integer waitHours = request.getWaitHours();
        if (action == NegotiationReactionAction.NEXT_STEP_NOW) {
            waitHours = null;
        }
        return new CounterRuleValue(
                request.getMinimumDiscountPercent(),
                action,
                waitHours
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

    private TargetMode resolveTargetMode(CreateBotConfigurationRequest request) {
        return request.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : request.getTargetMode();
    }

    private void validatePriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        if (minPrice == null || maxPrice == null) {
            return;
        }
        if (minPrice.signum() < 0 || maxPrice.signum() < 0) {
            throw new IllegalArgumentException("Listing prices cannot be negative.");
        }
        if (minPrice.compareTo(maxPrice) > 0) {
            throw new IllegalArgumentException(
                    "Minimum listing price cannot be greater than maximum listing price."
            );
        }
    }

    private void requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalizeRequiredText(String value) {
        return value.trim().replaceAll("\\s+", " ");
    }

    private record ResolvedStepPolicy(
            NegotiationReactionAction rejectionAction,
            Integer rejectionWaitHours,
            Integer readWaitHours,
            Integer unreadWaitHours,
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

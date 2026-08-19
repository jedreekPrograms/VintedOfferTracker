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
import pl.flipbot.negotiation.NegotiationStep;
import pl.flipbot.negotiation.dto.CreateNegotiationStepRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BotService {

    private final BotRepository botRepository;
    private final BotConfigurationRepository botConfigurationRepository;
    private final ListingRepository listingRepository;
    private final BotMapper botMapper;

    public List<BotResponse> getAllBots() {
        return botRepository.findAll()
                .stream()
                .map(botMapper::map)
                .toList();
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

        CreateBotConfigurationRequest configurationRequest =
                request.getConfiguration();

        validateConfiguration(configurationRequest);

        TargetMode targetMode = resolveTargetMode(configurationRequest);
        boolean autoRaiseOfferToVintedMinimum = Boolean.TRUE.equals(
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
                .model(
                        targetMode == TargetMode.VINTED_MODEL
                                ? normalizeRequiredText(configurationRequest.getModel())
                                : null
                )
                .searchQuery(
                        targetMode == TargetMode.SEARCH_QUERY
                                ? normalizeRequiredText(configurationRequest.getSearchQuery())
                                : null
                )
                .minPrice(configurationRequest.getMinPrice())
                .maxPrice(configurationRequest.getMaxPrice())
                .autoRaiseOfferToVintedMinimum(autoRaiseOfferToVintedMinimum)
                .maxAutomaticOffer(
                        autoRaiseOfferToVintedMinimum
                                ? configurationRequest.getMaxAutomaticOffer()
                                : null
                )
                .dailyNegotiationBudget(configurationRequest.getDailyNegotiationBudget())
                .bot(savedBot)
                .build();

        savedBot.setConfiguration(configuration);

        replaceNegotiationSteps(
                configuration,
                configurationRequest.getNegotiationSteps()
        );

        botConfigurationRepository.save(configuration);

        return botMapper.map(savedBot);
    }

    @Transactional
    public BotResponse updateBot(
            Long botId,
            UpdateBotRequest request
    ) {
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

        CreateBotConfigurationRequest configurationRequest =
                request.getConfiguration();

        validateConfiguration(configurationRequest);

        TargetMode requestedTargetMode = resolveTargetMode(configurationRequest);
        boolean requestedAdaptiveMode = Boolean.TRUE.equals(
                configurationRequest.getAutoRaiseOfferToVintedMinimum()
        );
        String normalizedEmail = normalizeRequiredText(request.getEmail());

        boolean negotiationStepsChanged = negotiationStepsChanged(
                configuration,
                configurationRequest.getNegotiationSteps()
        );
        boolean priceRangeChanged = !sameDecimal(
                configuration.getMinPrice(),
                configurationRequest.getMinPrice()
        ) || !sameDecimal(
                configuration.getMaxPrice(),
                configurationRequest.getMaxPrice()
        );
        boolean adaptiveModeChanged = Boolean.TRUE.equals(
                configuration.getAutoRaiseOfferToVintedMinimum()
        ) != requestedAdaptiveMode;
        boolean globalCapIncreased = isGlobalCapIncreased(
                configuration.getMaxAutomaticOffer(),
                requestedAdaptiveMode
                        ? configurationRequest.getMaxAutomaticOffer()
                        : null
        );

        List<Listing> activeListings = getActiveNegotiationListings(botId);

        validateActiveNegotiationEdit(
                bot,
                configuration,
                request,
                configurationRequest,
                requestedTargetMode,
                requestedAdaptiveMode,
                normalizedEmail,
                negotiationStepsChanged,
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

        configuration.setMarketplace(configurationRequest.getMarketplace());
        configuration.setCategoryPath(
                new ArrayList<>(configurationRequest.getCategoryPath())
        );
        configuration.setBrand(
                normalizeRequiredText(configurationRequest.getBrand())
        );
        configuration.setTargetMode(requestedTargetMode);
        configuration.setModel(
                requestedTargetMode == TargetMode.VINTED_MODEL
                        ? normalizeRequiredText(configurationRequest.getModel())
                        : null
        );
        configuration.setSearchQuery(
                requestedTargetMode == TargetMode.SEARCH_QUERY
                        ? normalizeRequiredText(configurationRequest.getSearchQuery())
                        : null
        );
        configuration.setMinPrice(configurationRequest.getMinPrice());
        configuration.setMaxPrice(configurationRequest.getMaxPrice());
        configuration.setAutoRaiseOfferToVintedMinimum(requestedAdaptiveMode);
        configuration.setMaxAutomaticOffer(
                requestedAdaptiveMode
                        ? configurationRequest.getMaxAutomaticOffer()
                        : null
        );
        configuration.setDailyNegotiationBudget(
                configurationRequest.getDailyNegotiationBudget()
        );

        if (negotiationStepsChanged) {
            replaceNegotiationSteps(
                    configuration,
                    configurationRequest.getNegotiationSteps()
            );
        }

        if (negotiationStepsChanged || adaptiveModeChanged || globalCapIncreased) {
            resetSkippedOfferTooLowListings(botId);
        }

        if (priceRangeChanged) {
            resetSkippedOutsidePriceRangeListings(botId);
        }

        return botMapper.map(bot);
    }

    @Transactional
    public void startBot(Long botId) {
        Bot bot = getBotEntity(botId);
        bot.setStatus(BotStatus.RUNNING);
    }

    @Transactional
    public void stopBot(Long botId) {
        Bot bot = getBotEntity(botId);
        bot.setStatus(BotStatus.STOPPED);
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
                || !Boolean.TRUE.equals(
                configuration.getAutoRaiseOfferToVintedMinimum()
        )) {
            return null;
        }

        return configuration.getNegotiationSteps()
                .stream()
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
            boolean negotiationStepsChanged,
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

        if (!Objects.equals(
                configuration.getMarketplace(),
                requestedConfiguration.getMarketplace()
        )) {
            lockedChanges.add("marketplace");
        }

        if (!Objects.equals(
                configuration.getCategoryPath(),
                requestedConfiguration.getCategoryPath()
        )) {
            lockedChanges.add("category");
        }

        if (!sameNormalizedText(
                configuration.getBrand(),
                requestedConfiguration.getBrand()
        )) {
            lockedChanges.add("brand");
        }

        TargetMode currentTargetMode = configuration.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : configuration.getTargetMode();

        if (currentTargetMode != requestedTargetMode) {
            lockedChanges.add("target mode");
        }

        if (requestedTargetMode == TargetMode.VINTED_MODEL) {
            if (!sameNormalizedText(
                    configuration.getModel(),
                    requestedConfiguration.getModel()
            )) {
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

        if (negotiationStepsChanged) {
            lockedChanges.add("negotiation steps");
        }

        if (!lockedChanges.isEmpty()) {
            throw new IllegalStateException(
                    "Bot has active negotiations. These fields cannot be changed "
                            + "until all active negotiations are finished: "
                            + String.join(", ", lockedChanges)
                            + ". Allowed while active: bot name, listing min/max price, "
                            + "daily negotiation budget and global negotiation cap."
            );
        }
    }

    private boolean negotiationStepsChanged(
            BotConfiguration configuration,
            List<CreateNegotiationStepRequest> requestedSteps
    ) {
        List<NegotiationStep> existingSteps = configuration.getNegotiationSteps()
                .stream()
                .sorted(
                        Comparator.comparing(
                                step -> step.getStepNumber() == null
                                        ? Integer.MAX_VALUE
                                        : step.getStepNumber()
                        )
                )
                .toList();

        if (existingSteps.size() != requestedSteps.size()) {
            return true;
        }

        for (int index = 0; index < existingSteps.size(); index++) {
            NegotiationStep existingStep = existingSteps.get(index);
            CreateNegotiationStepRequest requestedStep = requestedSteps.get(index);

            if (!Objects.equals(existingStep.getStepNumber(), index + 1)
                    || !sameDecimal(
                    existingStep.getOfferPrice(),
                    requestedStep.getOfferPrice()
            )
                    || !sameDecimal(
                    existingStep.getMaxAcceptedCounterOffer(),
                    requestedStep.getMaxAcceptedCounterOffer()
            )
                    || !Objects.equals(
                    existingStep.getMessage(),
                    requestedStep.getMessage()
            )) {
                return true;
            }
        }

        return false;
    }

    private boolean isGlobalCapIncreased(
            BigDecimal currentCap,
            BigDecimal requestedCap
    ) {
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
        List<Listing> skippedListings =
                listingRepository.findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        ListingStatus.SKIPPED_OFFER_TOO_LOW
                );

        for (Listing listing : skippedListings) {
            listing.setStatus(ListingStatus.DISCOVERED);
        }
    }

    private void resetSkippedOutsidePriceRangeListings(Long botId) {
        List<Listing> skippedListings =
                listingRepository.findByBotIdAndStatusOrderByIdAsc(
                        botId,
                        ListingStatus.SKIPPED_OUTSIDE_PRICE_RANGE
                );

        for (Listing listing : skippedListings) {
            listing.setStatus(ListingStatus.DISCOVERED);
        }
    }

    private void replaceNegotiationSteps(
            BotConfiguration configuration,
            List<CreateNegotiationStepRequest> stepRequests
    ) {
        configuration.getNegotiationSteps().clear();

        int stepNumber = 1;
        for (CreateNegotiationStepRequest stepRequest : stepRequests) {
            NegotiationStep step = NegotiationStep.builder()
                    .stepNumber(stepNumber++)
                    .offerPrice(stepRequest.getOfferPrice())
                    .maxAcceptedCounterOffer(
                            stepRequest.getMaxAcceptedCounterOffer()
                    )
                    .message(stepRequest.getMessage())
                    .configuration(configuration)
                    .build();

            configuration.getNegotiationSteps().add(step);
        }
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
    }

    private TargetMode resolveTargetMode(CreateBotConfigurationRequest request) {
        return request.getTargetMode() == null
                ? TargetMode.VINTED_MODEL
                : request.getTargetMode();
    }

    private void validatePriceRange(
            BigDecimal minPrice,
            BigDecimal maxPrice
    ) {
        if (minPrice == null || maxPrice == null) {
            return;
        }

        if (minPrice.signum() < 0 || maxPrice.signum() < 0) {
            throw new IllegalArgumentException(
                    "Listing prices cannot be negative."
            );
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
}

package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts the configured negotiation ladder into an effective ladder for one
 * concrete listing.
 *
 * The configured step prices remain the source of relative progression:
 *
 *   900 -> 1000 means +11.11%
 *
 * If the first configured price is too low for Vinted, the first effective
 * offer is moved above Vinted's minimum and subsequent steps preserve those
 * configured percentages relative to the actually-sent previous offer.
 */
@Slf4j
public class AdaptiveNegotiationPricingService {

    static final BigDecimal VINTED_MIN_OFFER_RATIO =
            new BigDecimal("0.60");

    static final BigDecimal FIRST_OFFER_INCREMENT =
            new BigDecimal("50");

    static final BigDecimal NEXT_STEP_INCREMENT =
            new BigDecimal("10");

    private static final MathContext CALCULATION_CONTEXT =
            new MathContext(16, RoundingMode.HALF_UP);

    public boolean isAdaptiveModeEnabled(BotConfigurationDto configuration) {
        return configuration != null
                && Boolean.TRUE.equals(
                configuration.getAutoRaiseOfferToVintedMinimum()
        )
                && configuration.getMaxAutomaticOffer() != null
                && configuration.getMaxAutomaticOffer().signum() > 0;
    }

    /**
     * Price for the first adaptive retry after the configured first offer was
     * rejected as too low.
     *
     * We deliberately move to the NEXT 50 PLN bucket, even when the computed
     * minimum is already an exact multiple of 50. Example: 1200 -> 1250.
     */
    public Optional<BigDecimal> firstAdaptiveRetryPrice(
            ListingResponseDto listing,
            BotConfigurationDto configuration,
            BigDecimal configuredFirstOffer
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(configuration, "Configuration cannot be null");
        requirePositive(configuredFirstOffer, "Configured first offer");

        if (!isAdaptiveModeEnabled(configuration)) {
            return Optional.empty();
        }

        BigDecimal listingPrice = listing.originalPrice();
        if (listingPrice == null || listingPrice.signum() <= 0) {
            return Optional.empty();
        }

        BigDecimal estimatedVintedMinimum = listingPrice
                .multiply(VINTED_MIN_OFFER_RATIO)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal fromMinimum = roundStrictlyUp(
                estimatedVintedMinimum,
                FIRST_OFFER_INCREMENT
        );

        BigDecimal fromConfiguredOffer = roundStrictlyUp(
                configuredFirstOffer,
                FIRST_OFFER_INCREMENT
        );

        BigDecimal candidate = fromMinimum.max(fromConfiguredOffer)
                .setScale(2, RoundingMode.UNNECESSARY);

        if (exceedsGlobalCap(candidate, configuration)) {
            log.info(
                    "[ADAPTIVE PRICE] Marketplace listing {} needs first adaptive offer {}, but global negotiation cap is {}.",
                    listing.listingId(),
                    candidate,
                    configuration.getMaxAutomaticOffer()
            );
            return Optional.empty();
        }

        log.info(
                "[ADAPTIVE PRICE] Marketplace listing {} configured first offer={} was too low. Listing price={}, estimated Vinted minimum={}, first adaptive retry={} (strict +50 rounding), global cap={}.",
                listing.listingId(),
                configuredFirstOffer,
                listingPrice,
                estimatedVintedMinimum,
                candidate,
                configuration.getMaxAutomaticOffer()
        );

        return Optional.of(candidate);
    }

    public Optional<BigDecimal> nextFirstOfferRetry(
            BigDecimal previousRetry,
            BotConfigurationDto configuration
    ) {
        Objects.requireNonNull(configuration, "Configuration cannot be null");
        requirePositive(previousRetry, "Previous retry price");

        if (!isAdaptiveModeEnabled(configuration)) {
            return Optional.empty();
        }

        BigDecimal next = previousRetry
                .add(FIRST_OFFER_INCREMENT)
                .setScale(2, RoundingMode.UNNECESSARY);

        return exceedsGlobalCap(next, configuration)
                ? Optional.empty()
                : Optional.of(next);
    }

    /**
     * Returns an effective next step. The configured message and step number
     * are kept unchanged; only price thresholds are scaled.
     */
    public Optional<NegotiationStepDto> adaptNextStep(
            ListingResponseDto listing,
            NegotiationStepDto configuredNextStep,
            BotConfigurationDto configuration
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(configuredNextStep, "Next step cannot be null");
        Objects.requireNonNull(configuration, "Configuration cannot be null");

        if (!isAdaptiveModeEnabled(configuration)) {
            return Optional.of(copyStep(configuredNextStep));
        }

        NegotiationStepDto configuredCurrentStep = findConfiguredStep(
                listing.currentStep(),
                configuration.getNegotiationSteps()
        );

        BigDecimal actualCurrentOffer = listing.currentPrice();
        requirePositive(actualCurrentOffer, "Current actual offer");
        requirePositive(configuredCurrentStep.getOfferPrice(), "Configured current offer");
        requirePositive(configuredNextStep.getOfferPrice(), "Configured next offer");

        BigDecimal configuredRatio = configuredNextStep.getOfferPrice()
                .divide(
                        configuredCurrentStep.getOfferPrice(),
                        CALCULATION_CONTEXT
                );

        BigDecimal rawNextOffer = actualCurrentOffer
                .multiply(configuredRatio, CALCULATION_CONTEXT);

        BigDecimal effectiveNextOffer = roundUp(
                rawNextOffer,
                NEXT_STEP_INCREMENT
        ).setScale(2, RoundingMode.UNNECESSARY);

        if (effectiveNextOffer.compareTo(actualCurrentOffer) <= 0) {
            log.warn(
                    "[ADAPTIVE PRICE] Cannot create an increasing next step for listing {}. Current actual={}, configured current={}, configured next={}, calculated next={}.",
                    listing.listingId(),
                    actualCurrentOffer,
                    configuredCurrentStep.getOfferPrice(),
                    configuredNextStep.getOfferPrice(),
                    effectiveNextOffer
            );
            return Optional.empty();
        }

        if (exceedsGlobalCap(effectiveNextOffer, configuration)) {
            log.info(
                    "[ADAPTIVE PRICE] Next step {} for listing {} would be {}, above global negotiation cap {}. No higher automatic offer will be sent.",
                    configuredNextStep.getStepNumber(),
                    listing.listingId(),
                    effectiveNextOffer,
                    configuration.getMaxAutomaticOffer()
            );
            return Optional.empty();
        }

        NegotiationStepDto effectiveStep = copyStep(configuredNextStep);
        effectiveStep.setOfferPrice(effectiveNextOffer);
        effectiveStep.setMaxAcceptedCounterOffer(
                scaleAcceptedCounterOffer(
                        effectiveNextOffer,
                        configuredNextStep,
                        configuration
                )
        );

        log.info(
                "[ADAPTIVE PRICE] Listing {} step {} scaled from configured {} to effective {} using previous actual offer {} and configured step ratio {}. Effective accepted counteroffer limit={}, global cap={}.",
                listing.listingId(),
                configuredNextStep.getStepNumber(),
                configuredNextStep.getOfferPrice(),
                effectiveNextOffer,
                actualCurrentOffer,
                configuredRatio,
                effectiveStep.getMaxAcceptedCounterOffer(),
                configuration.getMaxAutomaticOffer()
        );

        return Optional.of(effectiveStep);
    }

    public BigDecimal effectiveAcceptedCounterOfferLimit(
            ListingResponseDto listing,
            NegotiationStepDto configuredCurrentStep,
            BotConfigurationDto configuration
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(configuredCurrentStep, "Current step cannot be null");
        Objects.requireNonNull(configuration, "Configuration cannot be null");

        BigDecimal configuredLimit =
                configuredCurrentStep.getMaxAcceptedCounterOffer();

        if (configuredLimit == null) {
            return null;
        }

        if (!isAdaptiveModeEnabled(configuration)) {
            return configuredLimit;
        }

        BigDecimal actualCurrentOffer = listing.currentPrice();
        requirePositive(actualCurrentOffer, "Current actual offer");

        BigDecimal effective = scaleAcceptedCounterOffer(
                actualCurrentOffer,
                configuredCurrentStep,
                configuration
        );

        log.info(
                "[ADAPTIVE PRICE] Listing {} current step {} actual offer={} configured accepted limit={} -> effective accepted limit={} (global cap={}).",
                listing.listingId(),
                configuredCurrentStep.getStepNumber(),
                actualCurrentOffer,
                configuredLimit,
                effective,
                configuration.getMaxAutomaticOffer()
        );

        return effective;
    }

    private BigDecimal scaleAcceptedCounterOffer(
            BigDecimal effectiveOfferPrice,
            NegotiationStepDto configuredStep,
            BotConfigurationDto configuration
    ) {
        BigDecimal configuredAccepted =
                configuredStep.getMaxAcceptedCounterOffer();

        if (configuredAccepted == null) {
            return null;
        }

        requirePositive(configuredStep.getOfferPrice(), "Configured step offer");
        requirePositive(configuredAccepted, "Configured accepted counteroffer");

        BigDecimal ratio = configuredAccepted.divide(
                configuredStep.getOfferPrice(),
                CALCULATION_CONTEXT
        );

        BigDecimal scaled = roundUp(
                effectiveOfferPrice.multiply(ratio, CALCULATION_CONTEXT),
                NEXT_STEP_INCREMENT
        ).setScale(2, RoundingMode.UNNECESSARY);

        BigDecimal cap = configuration.getMaxAutomaticOffer();
        if (cap != null && scaled.compareTo(cap) > 0) {
            return cap.setScale(2, RoundingMode.UNNECESSARY);
        }

        return scaled;
    }

    private NegotiationStepDto findConfiguredStep(
            Integer stepNumber,
            List<NegotiationStepDto> steps
    ) {
        if (stepNumber == null) {
            throw new IllegalStateException("Current negotiation step is missing");
        }

        if (steps == null || steps.isEmpty()) {
            throw new IllegalStateException("Bot has no negotiation steps");
        }

        return steps.stream()
                .filter(Objects::nonNull)
                .filter(step -> Objects.equals(step.getStepNumber(), stepNumber))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Cannot find configured negotiation step " + stepNumber
                        )
                );
    }

    public NegotiationStepDto firstConfiguredStep(
            BotConfigurationDto configuration
    ) {
        if (configuration == null
                || configuration.getNegotiationSteps() == null
                || configuration.getNegotiationSteps().isEmpty()) {
            throw new IllegalStateException("Bot has no negotiation steps");
        }

        return configuration.getNegotiationSteps()
                .stream()
                .filter(Objects::nonNull)
                .filter(step -> step.getStepNumber() != null)
                .min(Comparator.comparing(NegotiationStepDto::getStepNumber))
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Bot has no valid negotiation steps"
                        )
                );
    }

    private boolean exceedsGlobalCap(
            BigDecimal price,
            BotConfigurationDto configuration
    ) {
        BigDecimal cap = configuration.getMaxAutomaticOffer();
        return cap != null && price.compareTo(cap) > 0;
    }

    static BigDecimal roundStrictlyUp(
            BigDecimal value,
            BigDecimal increment
    ) {
        requirePositiveStatic(value, "Value");
        requirePositiveStatic(increment, "Increment");

        BigDecimal buckets = value.divide(
                increment,
                0,
                RoundingMode.FLOOR
        );

        return buckets.add(BigDecimal.ONE)
                .multiply(increment);
    }

    static BigDecimal roundUp(
            BigDecimal value,
            BigDecimal increment
    ) {
        requirePositiveStatic(value, "Value");
        requirePositiveStatic(increment, "Increment");

        BigDecimal buckets = value.divide(
                increment,
                0,
                RoundingMode.CEILING
        );

        return buckets.multiply(increment);
    }

    private NegotiationStepDto copyStep(NegotiationStepDto source) {
        NegotiationStepDto copy = new NegotiationStepDto();
        copy.setStepNumber(source.getStepNumber());
        copy.setOfferPrice(source.getOfferPrice());
        copy.setMaxAcceptedCounterOffer(source.getMaxAcceptedCounterOffer());
        copy.setMessage(source.getMessage());
        return copy;
    }

    private void requirePositive(BigDecimal value, String label) {
        requirePositiveStatic(value, label);
    }

    private static void requirePositiveStatic(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero");
        }
    }
}

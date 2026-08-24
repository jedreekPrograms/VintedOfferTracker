package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.model.SellerCounterOfferRuleDto;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Converts the configured negotiation ladder into an effective ladder for one
 * concrete listing.
 *
 * Two semantics exist because active negotiations are immutable snapshots:
 * LEGACY_RATIO preserves conversations that predate the strategy-snapshot
 * rollout. New snapshots use DECREASING_CONCESSIONS: each later concession is
 * moderately smaller, which signals that the buyer is approaching a genuine
 * reservation price instead of rewarding repeated rejection with larger moves.
 */
@Slf4j
public class AdaptiveNegotiationPricingService {

    static final BigDecimal VINTED_MIN_OFFER_RATIO = new BigDecimal("0.60");
    static final BigDecimal FIRST_OFFER_INCREMENT = new BigDecimal("50");
    static final BigDecimal NEXT_STEP_INCREMENT = new BigDecimal("10");

    /** Moderate decay; research finds moderate rather than extreme decreases strongest. */
    static final BigDecimal CONCESSION_DECAY = new BigDecimal("0.70");

    private static final MathContext CALCULATION_CONTEXT =
            new MathContext(16, RoundingMode.HALF_UP);

    public boolean isAdaptiveModeEnabled(BotConfigurationDto configuration) {
        return configuration != null
                && Boolean.TRUE.equals(configuration.getAutoRaiseOfferToVintedMinimum())
                && configuration.getMaxAutomaticOffer() != null
                && configuration.getMaxAutomaticOffer().signum() > 0;
    }

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
                    listing.listingId(), candidate, configuration.getMaxAutomaticOffer()
            );
            return Optional.empty();
        }

        log.info(
                "[ADAPTIVE PRICE] Marketplace listing {} configured first offer={} was too low. Listing price={}, estimated Vinted minimum={}, first adaptive retry={} (strict +50 rounding), global cap={}.",
                listing.listingId(), configuredFirstOffer, listingPrice,
                estimatedVintedMinimum, candidate, configuration.getMaxAutomaticOffer()
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

        BigDecimal next = previousRetry.add(FIRST_OFFER_INCREMENT)
                .setScale(2, RoundingMode.UNNECESSARY);
        return exceedsGlobalCap(next, configuration)
                ? Optional.empty()
                : Optional.of(next);
    }

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

        if (NegotiationStrategyResolver.DECREASING_CONCESSIONS.equals(
                configuration.getNegotiationPricingMode()
        )) {
            return adaptWithDecreasingConcessions(
                    listing,
                    configuredNextStep,
                    configuration
            );
        }

        return adaptWithLegacyRatio(listing, configuredNextStep, configuration);
    }

    private Optional<NegotiationStepDto> adaptWithDecreasingConcessions(
            ListingResponseDto listing,
            NegotiationStepDto configuredNextStep,
            BotConfigurationDto configuration
    ) {
        BigDecimal actualCurrentOffer = listing.currentPrice();
        BigDecimal cap = configuration.getMaxAutomaticOffer();
        requirePositive(actualCurrentOffer, "Current actual offer");
        requirePositive(cap, "Global negotiation cap");

        if (cap.compareTo(actualCurrentOffer) <= 0) {
            return Optional.empty();
        }

        List<NegotiationStepDto> orderedSteps = orderedSteps(configuration);
        int currentIndex = indexOfStep(orderedSteps, listing.currentStep());
        int nextIndex = indexOfStep(orderedSteps, configuredNextStep.getStepNumber());

        if (nextIndex <= currentIndex) {
            throw new IllegalStateException("Next negotiation step must follow the current step");
        }

        int remainingTransitions = orderedSteps.size() - currentIndex - 1;
        if (remainingTransitions <= 0) {
            return Optional.empty();
        }

        BigDecimal effectiveNextOffer;
        if (remainingTransitions == 1) {
            effectiveNextOffer = cap.setScale(2, RoundingMode.UNNECESSARY);
        } else {
            BigDecimal remainingGap = cap.subtract(actualCurrentOffer);
            BigDecimal firstWeight = BigDecimal.ONE;
            BigDecimal weightSum = BigDecimal.ZERO;
            BigDecimal weight = BigDecimal.ONE;

            for (int index = 0; index < remainingTransitions; index++) {
                weightSum = weightSum.add(weight, CALCULATION_CONTEXT);
                weight = weight.multiply(CONCESSION_DECAY, CALCULATION_CONTEXT);
            }

            BigDecimal rawConcession = remainingGap
                    .multiply(firstWeight, CALCULATION_CONTEXT)
                    .divide(weightSum, CALCULATION_CONTEXT);

            BigDecimal roundedConcession = roundUp(
                    rawConcession,
                    NEXT_STEP_INCREMENT
            );
            effectiveNextOffer = actualCurrentOffer
                    .add(roundedConcession)
                    .min(cap)
                    .setScale(2, RoundingMode.UNNECESSARY);
        }

        if (effectiveNextOffer.compareTo(actualCurrentOffer) <= 0) {
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
                "[ADAPTIVE PRICE] Listing {} uses DECREASING_CONCESSIONS. Current step={}, actual={}, next step={}, effective next={}, cap={}, remaining transitions={}, decay={}. Effective accepted counteroffer limit={}.",
                listing.listingId(), listing.currentStep(), actualCurrentOffer,
                configuredNextStep.getStepNumber(), effectiveNextOffer, cap,
                remainingTransitions, CONCESSION_DECAY,
                effectiveStep.getMaxAcceptedCounterOffer()
        );
        return Optional.of(effectiveStep);
    }

    private Optional<NegotiationStepDto> adaptWithLegacyRatio(
            ListingResponseDto listing,
            NegotiationStepDto configuredNextStep,
            BotConfigurationDto configuration
    ) {
        NegotiationStepDto configuredCurrentStep = findConfiguredStep(
                listing.currentStep(), configuration.getNegotiationSteps()
        );

        BigDecimal actualCurrentOffer = listing.currentPrice();
        requirePositive(actualCurrentOffer, "Current actual offer");
        requirePositive(configuredCurrentStep.getOfferPrice(), "Configured current offer");
        requirePositive(configuredNextStep.getOfferPrice(), "Configured next offer");

        BigDecimal configuredRatio = configuredNextStep.getOfferPrice()
                .divide(configuredCurrentStep.getOfferPrice(), CALCULATION_CONTEXT);
        BigDecimal rawNextOffer = actualCurrentOffer
                .multiply(configuredRatio, CALCULATION_CONTEXT);
        BigDecimal effectiveNextOffer = roundUp(rawNextOffer, NEXT_STEP_INCREMENT)
                .setScale(2, RoundingMode.UNNECESSARY);

        if (effectiveNextOffer.compareTo(actualCurrentOffer) <= 0) {
            log.warn(
                    "[ADAPTIVE PRICE] Cannot create an increasing legacy next step for listing {}. Current actual={}, configured current={}, configured next={}, calculated next={}.",
                    listing.listingId(), actualCurrentOffer,
                    configuredCurrentStep.getOfferPrice(), configuredNextStep.getOfferPrice(),
                    effectiveNextOffer
            );
            return Optional.empty();
        }

        if (exceedsGlobalCap(effectiveNextOffer, configuration)) {
            log.info(
                    "[ADAPTIVE PRICE] Legacy-ratio next step {} for listing {} would be {}, above global negotiation cap {}.",
                    configuredNextStep.getStepNumber(), listing.listingId(),
                    effectiveNextOffer, configuration.getMaxAutomaticOffer()
            );
            return Optional.empty();
        }

        NegotiationStepDto effectiveStep = copyStep(configuredNextStep);
        effectiveStep.setOfferPrice(effectiveNextOffer);
        effectiveStep.setMaxAcceptedCounterOffer(
                scaleAcceptedCounterOffer(effectiveNextOffer, configuredNextStep, configuration)
        );

        log.info(
                "[ADAPTIVE PRICE] Listing {} uses LEGACY_RATIO. Step {} scaled from configured {} to effective {} using previous actual offer {} and ratio {}. Effective accepted limit={}, cap={}.",
                listing.listingId(), configuredNextStep.getStepNumber(),
                configuredNextStep.getOfferPrice(), effectiveNextOffer,
                actualCurrentOffer, configuredRatio,
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

        BigDecimal configuredLimit = configuredCurrentStep.getMaxAcceptedCounterOffer();
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
                "[ADAPTIVE PRICE] Listing {} current step {} actual offer={} configured accepted limit={} -> effective accepted limit={} (cap={}).",
                listing.listingId(), configuredCurrentStep.getStepNumber(),
                actualCurrentOffer, configuredLimit, effective,
                configuration.getMaxAutomaticOffer()
        );
        return effective;
    }

    private BigDecimal scaleAcceptedCounterOffer(
            BigDecimal effectiveOfferPrice,
            NegotiationStepDto configuredStep,
            BotConfigurationDto configuration
    ) {
        BigDecimal configuredAccepted = configuredStep.getMaxAcceptedCounterOffer();
        if (configuredAccepted == null) {
            return null;
        }

        requirePositive(configuredStep.getOfferPrice(), "Configured step offer");
        requirePositive(configuredAccepted, "Configured accepted counteroffer");

        BigDecimal ratio = configuredAccepted.divide(
                configuredStep.getOfferPrice(), CALCULATION_CONTEXT
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
        return orderedSteps(steps).stream()
                .filter(step -> Objects.equals(step.getStepNumber(), stepNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot find configured negotiation step " + stepNumber
                ));
    }

    private List<NegotiationStepDto> orderedSteps(BotConfigurationDto configuration) {
        return orderedSteps(configuration.getNegotiationSteps());
    }

    private List<NegotiationStepDto> orderedSteps(List<NegotiationStepDto> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalStateException("Bot has no negotiation steps");
        }
        return steps.stream()
                .filter(Objects::nonNull)
                .filter(step -> step.getStepNumber() != null)
                .sorted(Comparator.comparing(NegotiationStepDto::getStepNumber))
                .toList();
    }

    private int indexOfStep(List<NegotiationStepDto> steps, Integer stepNumber) {
        if (stepNumber == null) {
            throw new IllegalStateException("Current negotiation step is missing");
        }
        for (int index = 0; index < steps.size(); index++) {
            if (Objects.equals(steps.get(index).getStepNumber(), stepNumber)) {
                return index;
            }
        }
        throw new IllegalStateException("Cannot find negotiation step " + stepNumber);
    }

    public NegotiationStepDto firstConfiguredStep(BotConfigurationDto configuration) {
        return orderedSteps(configuration).getFirst();
    }

    private boolean exceedsGlobalCap(
            BigDecimal price,
            BotConfigurationDto configuration
    ) {
        BigDecimal cap = configuration.getMaxAutomaticOffer();
        return cap != null && price.compareTo(cap) > 0;
    }

    static BigDecimal roundStrictlyUp(BigDecimal value, BigDecimal increment) {
        requirePositiveStatic(value, "Value");
        requirePositiveStatic(increment, "Increment");
        BigDecimal buckets = value.divide(increment, 0, RoundingMode.FLOOR);
        return buckets.add(BigDecimal.ONE).multiply(increment);
    }

    static BigDecimal roundUp(BigDecimal value, BigDecimal increment) {
        requirePositiveStatic(value, "Value");
        requirePositiveStatic(increment, "Increment");
        BigDecimal buckets = value.divide(increment, 0, RoundingMode.CEILING);
        return buckets.multiply(increment);
    }

    private NegotiationStepDto copyStep(NegotiationStepDto source) {
        NegotiationStepDto copy = new NegotiationStepDto();
        copy.setStepNumber(source.getStepNumber());
        copy.setOfferPrice(source.getOfferPrice());
        copy.setMaxAcceptedCounterOffer(source.getMaxAcceptedCounterOffer());
        copy.setMessage(source.getMessage());
        copy.setRejectionAction(source.getRejectionAction());
        copy.setRejectionWaitHours(source.getRejectionWaitHours());
        copy.setCounterOfferDefaultAction(source.getCounterOfferDefaultAction());
        copy.setCounterOfferDefaultWaitHours(source.getCounterOfferDefaultWaitHours());

        List<SellerCounterOfferRuleDto> rules = new ArrayList<>();
        if (source.getCounterOfferRules() != null) {
            for (SellerCounterOfferRuleDto rule : source.getCounterOfferRules()) {
                SellerCounterOfferRuleDto copyRule = new SellerCounterOfferRuleDto();
                copyRule.setMinimumDiscountPercent(rule.getMinimumDiscountPercent());
                copyRule.setAction(rule.getAction());
                copyRule.setWaitHours(rule.getWaitHours());
                rules.add(copyRule);
            }
        }
        copy.setCounterOfferRules(rules);
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

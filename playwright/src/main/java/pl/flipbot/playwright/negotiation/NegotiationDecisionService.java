package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationReactionAction;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.model.SellerCounterOfferRuleDto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class NegotiationDecisionService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final AdaptiveNegotiationPricingService pricingService;
    private final Clock clock;

    public NegotiationDecisionService() {
        this(new AdaptiveNegotiationPricingService(), Clock.systemDefaultZone());
    }

    NegotiationDecisionService(Clock clock) {
        this(new AdaptiveNegotiationPricingService(), clock);
    }

    NegotiationDecisionService(
            AdaptiveNegotiationPricingService pricingService,
            Clock clock
    ) {
        this.pricingService = Objects.requireNonNull(pricingService);
        this.clock = Objects.requireNonNull(clock);
    }

    public NegotiationDecision decide(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot,
            BotConfigurationDto configuration
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(snapshot, "Conversation snapshot cannot be null");
        Objects.requireNonNull(configuration, "Bot configuration cannot be null");

        validateListing(listing);
        validateNegotiationSteps(configuration.getNegotiationSteps());

        NegotiationDecision decision = switch (snapshot.result()) {
            case PENDING -> NegotiationDecision.waitForSeller();
            case ACCEPTED -> NegotiationDecision.actionRequiredAfterAcceptance();
            case REJECTED -> decideAfterRejection(
                    listing,
                    snapshot,
                    configuration
            );
            case SELLER_COUNTER_OFFER -> decideAfterSellerCounterOffer(
                    listing,
                    snapshot,
                    configuration
            );
            case UNKNOWN -> NegotiationDecision.unknown();
        };

        logDecision(listing, snapshot, decision);
        return decision;
    }

    private NegotiationDecision decideAfterRejection(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot,
            BotConfigurationDto configuration
    ) {
        NegotiationStepDto currentStep = findCurrentStep(
                listing.currentStep(),
                configuration.getNegotiationSteps()
        );

        Optional<NegotiationStepDto> effectiveNextStep = findEffectiveNextStep(
                listing,
                configuration
        );

        if (effectiveNextStep.isEmpty()) {
            return NegotiationDecision.rejected(
                    null,
                    noNextStepReason(listing, configuration, "The seller rejected the latest offer")
            );
        }

        ReactionPolicy policy = rejectionPolicy(currentStep);
        return applyReactionPolicy(
                listing,
                snapshot,
                effectiveNextStep.get(),
                null,
                policy,
                "The seller formally rejected step " + listing.currentStep()
        );
    }

    private NegotiationDecision decideAfterSellerCounterOffer(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot,
            BotConfigurationDto configuration
    ) {
        BigDecimal sellerPrice = snapshot.sellerCounterOfferPrice();
        if (sellerPrice == null) {
            throw new IllegalStateException(
                    "Snapshot is classified as SELLER_COUNTER_OFFER, but contains no seller counteroffer price"
            );
        }

        NegotiationStepDto currentStep = findCurrentStep(
                listing.currentStep(),
                configuration.getNegotiationSteps()
        );

        BigDecimal acceptedLimit = pricingService.effectiveAcceptedCounterOfferLimit(
                listing,
                currentStep,
                configuration
        );

        /* Explicit per-step acceptance threshold always has first priority. */
        if (acceptedLimit != null && sellerPrice.compareTo(acceptedLimit) <= 0) {
            return NegotiationDecision.actionRequiredForCounterOffer(sellerPrice);
        }

        Optional<NegotiationStepDto> effectiveNextStep = findEffectiveNextStep(
                listing,
                configuration
        );

        if (effectiveNextStep.isEmpty()) {
            return NegotiationDecision.rejected(
                    sellerPrice,
                    noNextStepReason(
                            listing,
                            configuration,
                            "The seller counteroffer " + sellerPrice
                                    + " is above the accepted limit " + acceptedLimit
                    )
            );
        }

        /*
         * Never answer a seller's concrete price by offering MORE than the
         * seller just asked for. If their counteroffer is already at/below our
         * planned next concession, it is economically better than the next
         * automatic step, so surface it as ACTION_REQUIRED immediately.
         */
        if (sellerPrice.compareTo(effectiveNextStep.get().getOfferPrice()) <= 0) {
            return new NegotiationDecision(
                    NegotiationDecisionType.MARK_ACTION_REQUIRED,
                    null,
                    sellerPrice,
                    "The seller counteroffer " + sellerPrice
                            + " is at or below our planned next offer "
                            + effectiveNextStep.get().getOfferPrice()
                            + "; sending a higher counteroffer would be irrational"
            );
        }

        BigDecimal discountPercent = discountFromOriginalPrice(listing, sellerPrice);
        ReactionPolicy policy = counterOfferPolicy(currentStep, discountPercent);

        return applyReactionPolicy(
                listing,
                snapshot,
                effectiveNextStep.get(),
                sellerPrice,
                policy,
                "The seller proposed " + sellerPrice
                        + ", which is " + discountPercent.stripTrailingZeros().toPlainString()
                        + "% below the original listing price " + listing.originalPrice()
        );
    }

    private NegotiationDecision applyReactionPolicy(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot,
            NegotiationStepDto nextStep,
            BigDecimal sellerCounterOffer,
            ReactionPolicy policy,
            String contextReason
    ) {
        if (policy.action() == NegotiationReactionAction.NEXT_STEP_NOW) {
            return NegotiationDecision.sendNextStep(
                    nextStep,
                    sellerCounterOffer,
                    contextReason + ". Policy: send the next step immediately."
            );
        }

        if (policy.action() != NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP) {
            throw new IllegalStateException(
                    "Unsupported negotiation reaction action: " + policy.action()
            );
        }

        if (policy.waitHours() == null || policy.waitHours() <= 0) {
            throw new IllegalStateException(
                    "WAIT_BEFORE_NEXT_STEP requires a positive waitHours value"
            );
        }

        String expectedFingerprint = NegotiationResponseFingerprint.create(
                listing,
                snapshot
        );
        LocalDateTime detectedAt = matchingFormalResponseDetectedAt(
                listing,
                expectedFingerprint
        );

        if (detectedAt == null) {
            return NegotiationDecision.wait(
                    sellerCounterOffer,
                    contextReason
                            + ". Policy: wait " + policy.waitHours()
                            + "h before the next step. The formal response timer has just been registered; "
                            + "the bot will not guess a start time if persistence is unavailable."
            );
        }

        LocalDateTime nextActionAt = detectedAt.plusHours(policy.waitHours());
        LocalDateTime now = LocalDateTime.now(clock);

        if (now.isBefore(nextActionAt)) {
            return NegotiationDecision.wait(
                    sellerCounterOffer,
                    contextReason
                            + ". Policy: wait " + policy.waitHours()
                            + "h. Response first detected at " + detectedAt
                            + "; next step is eligible at " + nextActionAt + "."
            );
        }

        return NegotiationDecision.sendNextStep(
                nextStep,
                sellerCounterOffer,
                contextReason
                        + ". The configured " + policy.waitHours()
                        + "h wait elapsed at " + nextActionAt
                        + "; the next step may now be sent."
        );
    }

    private ReactionPolicy rejectionPolicy(NegotiationStepDto currentStep) {
        NegotiationReactionAction action = currentStep.getRejectionAction();
        if (action == null) {
            /* Backward-compatible behavior for an old backend response. */
            return new ReactionPolicy(NegotiationReactionAction.NEXT_STEP_NOW, null);
        }
        return new ReactionPolicy(action, currentStep.getRejectionWaitHours());
    }

    private ReactionPolicy counterOfferPolicy(
            NegotiationStepDto currentStep,
            BigDecimal discountPercent
    ) {
        SellerCounterOfferRuleDto bestRule = null;

        if (currentStep.getCounterOfferRules() != null) {
            for (SellerCounterOfferRuleDto rule : currentStep.getCounterOfferRules()) {
                if (rule == null
                        || rule.getMinimumDiscountPercent() == null
                        || rule.getAction() == null) {
                    continue;
                }

                if (discountPercent.compareTo(rule.getMinimumDiscountPercent()) >= 0
                        && (bestRule == null
                        || rule.getMinimumDiscountPercent().compareTo(
                        bestRule.getMinimumDiscountPercent()
                ) > 0)) {
                    bestRule = rule;
                }
            }
        }

        if (bestRule != null) {
            return new ReactionPolicy(bestRule.getAction(), bestRule.getWaitHours());
        }

        NegotiationReactionAction fallback = currentStep.getCounterOfferDefaultAction();
        if (fallback == null) {
            /* Backward-compatible old behavior. */
            return new ReactionPolicy(NegotiationReactionAction.NEXT_STEP_NOW, null);
        }

        return new ReactionPolicy(
                fallback,
                currentStep.getCounterOfferDefaultWaitHours()
        );
    }

    private BigDecimal discountFromOriginalPrice(
            ListingResponseDto listing,
            BigDecimal sellerPrice
    ) {
        BigDecimal originalPrice = listing.originalPrice();
        if (originalPrice == null || originalPrice.signum() <= 0) {
            throw new IllegalStateException(
                    "Cannot calculate seller discount because original listing price is invalid"
            );
        }

        BigDecimal discount = originalPrice
                .subtract(sellerPrice)
                .multiply(ONE_HUNDRED)
                .divide(originalPrice, 6, RoundingMode.HALF_UP);

        if (discount.signum() < 0) {
            return BigDecimal.ZERO.setScale(6);
        }
        if (discount.compareTo(ONE_HUNDRED) > 0) {
            return ONE_HUNDRED.setScale(6);
        }
        return discount;
    }

    private LocalDateTime matchingFormalResponseDetectedAt(
            ListingResponseDto listing,
            String expectedFingerprint
    ) {
        if (expectedFingerprint == null
                || !expectedFingerprint.equals(listing.formalResponseFingerprint())
                || listing.formalResponseDetectedAt() == null
                || listing.formalResponseDetectedAt().isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(listing.formalResponseDetectedAt());
        } catch (DateTimeParseException exception) {
            log.warn(
                    "[RESPONSE POLICY] Invalid formalResponseDetectedAt='{}' for listing {}. "
                            + "Failing closed and continuing to wait.",
                    listing.formalResponseDetectedAt(),
                    listing.listingId()
            );
            return null;
        }
    }

    private Optional<NegotiationStepDto> findEffectiveNextStep(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        Optional<NegotiationStepDto> configuredNextStep = findNextStep(
                listing.currentStep(),
                configuration.getNegotiationSteps()
        );

        if (configuredNextStep.isEmpty()) {
            return Optional.empty();
        }

        return pricingService.adaptNextStep(
                listing,
                configuredNextStep.get(),
                configuration
        );
    }

    private String noNextStepReason(
            ListingResponseDto listing,
            BotConfigurationDto configuration,
            String prefix
    ) {
        Optional<NegotiationStepDto> configuredNext = findNextStep(
                listing.currentStep(),
                configuration.getNegotiationSteps()
        );

        if (configuredNext.isEmpty()) {
            return prefix + " and there are no more negotiation steps";
        }

        return prefix
                + ", but the next adaptive offer would exceed the global negotiation cap "
                + configuration.getMaxAutomaticOffer();
    }

    private NegotiationStepDto findCurrentStep(
            Integer currentStepNumber,
            List<NegotiationStepDto> negotiationSteps
    ) {
        if (currentStepNumber == null) {
            throw new IllegalStateException("Negotiating listing has no current step");
        }

        return negotiationSteps.stream()
                .filter(Objects::nonNull)
                .filter(step -> Objects.equals(step.getStepNumber(), currentStepNumber))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot find negotiation configuration for current step "
                                + currentStepNumber
                ));
    }

    private Optional<NegotiationStepDto> findNextStep(
            Integer currentStepNumber,
            List<NegotiationStepDto> negotiationSteps
    ) {
        if (currentStepNumber == null) {
            throw new IllegalStateException("Negotiating listing has no current step");
        }

        return negotiationSteps.stream()
                .filter(Objects::nonNull)
                .filter(step -> step.getStepNumber() != null)
                .filter(step -> step.getStepNumber() > currentStepNumber)
                .min(Comparator.comparing(NegotiationStepDto::getStepNumber))
                .map(this::validateNextStep);
    }

    private NegotiationStepDto validateNextStep(NegotiationStepDto nextStep) {
        if (nextStep.getOfferPrice() == null || nextStep.getOfferPrice().signum() <= 0) {
            throw new IllegalStateException(
                    "Negotiation step " + nextStep.getStepNumber()
                            + " has an invalid offer price: " + nextStep.getOfferPrice()
            );
        }
        return nextStep;
    }

    private void validateListing(ListingResponseDto listing) {
        if (listing.id() == null) {
            throw new IllegalArgumentException("Backend listing ID cannot be null");
        }
        if (!"NEGOTIATING".equals(listing.status())) {
            throw new IllegalArgumentException(
                    "Negotiation decision can only be created for a NEGOTIATING listing. Backend listing: "
                            + listing.id() + ", status: " + listing.status()
            );
        }
        if (listing.currentStep() == null || listing.currentStep() <= 0) {
            throw new IllegalArgumentException(
                    "Negotiating listing " + listing.id()
                            + " has an invalid current step: " + listing.currentStep()
            );
        }
    }

    private void validateNegotiationSteps(List<NegotiationStepDto> negotiationSteps) {
        if (negotiationSteps == null || negotiationSteps.isEmpty()) {
            throw new IllegalStateException("Bot configuration has no negotiation steps");
        }
    }

    private void logDecision(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot,
            NegotiationDecision decision
    ) {
        log.info(
                "[DECISION] Backend listing {}, marketplace listing {}, current step {}, conversation result {}, "
                        + "seller counteroffer {}, decision {}, next step {}, effective next price {}, reason: {}",
                listing.id(),
                listing.listingId(),
                listing.currentStep(),
                snapshot.result(),
                snapshot.sellerCounterOfferPrice(),
                decision.type(),
                decision.nextStep() == null ? null : decision.nextStep().getStepNumber(),
                decision.nextStep() == null ? null : decision.nextStep().getOfferPrice(),
                decision.reason()
        );
    }

    private record ReactionPolicy(
            NegotiationReactionAction action,
            Integer waitHours
    ) {
    }
}

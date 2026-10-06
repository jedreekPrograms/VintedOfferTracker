package pl.flipbot.playwright.negotiation;

import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationReactionAction;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Handles Vinted PENDING state.
 *
 * A read indicator or a normal seller chat message is not a formal rejection,
 * but it is a clear seller reaction to our current step. It therefore reuses
 * the CURRENT STEP rejection policy:
 *
 * - NEXT_STEP_NOW -> continue immediately;
 * - WAIT_BEFORE_NEXT_STEP -> start the configured rejection wait from the
 *   first seller reaction detected for this step.
 *
 * With no seller reaction at all, a non-final step still needs a bounded
 * no-response timeout. A configured WAIT rejection delay is reused; legacy /
 * immediate rejection policies use a conservative 12h fallback so the ladder
 * does not cascade instantly without any seller activity.
 *
 * A completely untouched final PENDING step expires after 48h. If the seller
 * has read or messaged on that final step, its rejection policy applies first.
 */
public class PendingNegotiationPolicy {

    private static final int FINAL_STEP_PENDING_EXPIRY_HOURS = 48;
    private static final int FALLBACK_NON_RESPONSE_WAIT_HOURS = 12;

    private final Clock clock;
    private final AdaptiveNegotiationPricingService pricingService;

    public PendingNegotiationPolicy() {
        this(
                Clock.systemDefaultZone(),
                new AdaptiveNegotiationPricingService()
        );
    }

    PendingNegotiationPolicy(Clock clock) {
        this(
                clock,
                new AdaptiveNegotiationPricingService()
        );
    }

    PendingNegotiationPolicy(
            Clock clock,
            AdaptiveNegotiationPricingService pricingService
    ) {
        this.clock = Objects.requireNonNull(clock);
        this.pricingService = Objects.requireNonNull(pricingService);
    }

    public PendingNegotiationDecision decide(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot,
            BotConfigurationDto configuration
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(
                activitySnapshot,
                "Conversation activity snapshot cannot be null"
        );
        Objects.requireNonNull(
                configuration,
                "Bot configuration cannot be null"
        );

        NegotiationStepDto currentStep =
                findCurrentStep(listing, configuration);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime stepStartedAt =
                parseDateTime(listing.currentStepStartedAt());

        SellerReaction sellerReaction = resolveSellerReaction(
                listing,
                activitySnapshot,
                stepStartedAt,
                now
        );

        if (sellerReaction != null) {
            return applyRejectionPolicyToSellerReaction(
                    listing,
                    configuration,
                    currentStep,
                    sellerReaction,
                    now
            );
        }

        PendingNegotiationDecision finalStepDecision =
                decideUntouchedFinalStepPendingExpiry(
                        listing,
                        configuration,
                        stepStartedAt,
                        now
                );

        if (finalStepDecision != null) {
            return finalStepDecision;
        }

        return decideNoResponseTimeout(
                listing,
                configuration,
                currentStep,
                stepStartedAt,
                now
        );
    }

    private PendingNegotiationDecision applyRejectionPolicyToSellerReaction(
            ListingResponseDto listing,
            BotConfigurationDto configuration,
            NegotiationStepDto currentStep,
            SellerReaction reaction,
            LocalDateTime now
    ) {
        NegotiationReactionAction action =
                currentStep.getRejectionAction();

        if (action == null
                || action == NegotiationReactionAction.NEXT_STEP_NOW) {
            return continueOrExpire(
                    listing,
                    configuration,
                    reaction.reason()
                            + ". The current step uses the same policy as a rejection: send the next step immediately."
            );
        }

        if (action != NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP) {
            return PendingNegotiationDecision.waitForSeller(
                    reaction.reason()
                            + ". Unsupported rejection policy "
                            + action
                            + "; failing closed without sending another offer."
            );
        }

        Integer waitHours = currentStep.getRejectionWaitHours();
        if (waitHours == null || waitHours <= 0) {
            return PendingNegotiationDecision.waitForSeller(
                    reaction.reason()
                            + ". The step is configured to wait after rejection, but rejectionWaitHours is invalid; failing closed."
            );
        }

        LocalDateTime nextActionAt =
                reaction.detectedAt().plusHours(waitHours);

        if (now.isBefore(nextActionAt)) {
            return PendingNegotiationDecision.waitForSeller(
                    reaction.reason()
                            + ". This is treated like a rejection for timing. "
                            + "Step " + listing.currentStep()
                            + " is configured to wait "
                            + waitHours + "h after rejection, so the next step is eligible at "
                            + nextActionAt + "."
            );
        }

        return continueOrExpire(
                listing,
                configuration,
                reaction.reason()
                        + ". This is treated like a rejection for timing. "
                        + "The configured " + waitHours
                        + "h rejection wait elapsed at "
                        + nextActionAt + "."
        );
    }

    private PendingNegotiationDecision decideNoResponseTimeout(
            ListingResponseDto listing,
            BotConfigurationDto configuration,
            NegotiationStepDto currentStep,
            LocalDateTime stepStartedAt,
            LocalDateTime now
    ) {
        if (stepStartedAt == null) {
            return PendingNegotiationDecision.waitForSeller(
                    "Vinted still reports the latest offer as PENDING, but currentStepStartedAt is missing. "
                            + "The bot cannot safely calculate the no-response timer."
            );
        }

        int waitHours = noResponseWaitHours(currentStep);
        LocalDateTime nextActionAt =
                stepStartedAt.plusHours(waitHours);

        if (now.isBefore(nextActionAt)) {
            return PendingNegotiationDecision.waitForSeller(
                    "Vinted still reports step "
                            + listing.currentStep()
                            + " as PENDING with no seller message or read signal. "
                            + "The no-response timeout is "
                            + waitHours + "h; next step becomes eligible at "
                            + nextActionAt + "."
            );
        }

        return continueOrExpire(
                listing,
                configuration,
                "Vinted still reports step "
                        + listing.currentStep()
                        + " as PENDING with no seller reaction. "
                        + "The no-response timeout of "
                        + waitHours + "h elapsed at "
                        + nextActionAt + "."
        );
    }

    private PendingNegotiationDecision continueOrExpire(
            ListingResponseDto listing,
            BotConfigurationDto configuration,
            String reason
    ) {
        NegotiationStepDto configuredNextStep =
                findNextStep(listing, configuration);

        if (configuredNextStep == null) {
            return PendingNegotiationDecision.expire(
                    reason
                            + " There is no later configured negotiation step, so the conversation is closed as EXPIRED."
            );
        }

        Optional<NegotiationStepDto> effectiveNextStep =
                pricingService.adaptNextStep(
                        listing,
                        configuredNextStep,
                        configuration
                );

        if (effectiveNextStep.isPresent()) {
            return PendingNegotiationDecision.sendNextStep(
                    effectiveNextStep.get(),
                    reason
                            + " Effective next offer: "
                            + effectiveNextStep.get().getOfferPrice()
                            + "."
            );
        }

        return PendingNegotiationDecision.expire(
                reason
                        + " A later configured step exists, but its effective price cannot be sent within the automatic-offer cap."
        );
    }

    private SellerReaction resolveSellerReaction(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot,
            LocalDateTime stepStartedAt,
            LocalDateTime now
    ) {
        LocalDateTime persistedMessageAt =
                currentStepTimestamp(
                        parseDateTime(listing.sellerActivityAt()),
                        stepStartedAt
                );
        LocalDateTime persistedReadAt =
                currentStepTimestamp(
                        parseDateTime(listing.readDetectedAt()),
                        stepStartedAt
                );

        LocalDateTime detectedMessageAt = null;
        boolean messageDetected =
                activitySnapshot.inspectionSucceeded()
                        && activitySnapshot.latestOwnOfferFound()
                        && activitySnapshot.sellerMessageAfterLatestOwnOffer();

        if (messageDetected) {
            detectedMessageAt = currentStepTimestamp(
                    activitySnapshot.latestSellerMessageAt(),
                    stepStartedAt
            );
            if (detectedMessageAt == null) {
                detectedMessageAt = persistedMessageAt == null
                        ? now
                        : persistedMessageAt;
            }
        }

        LocalDateTime detectedReadAt = null;
        boolean readDetected =
                activitySnapshot.inspectionSucceeded()
                        && activitySnapshot.latestOwnOfferFound()
                        && activitySnapshot.readIndicatorAfterLatestOwnOffer();

        if (readDetected) {
            detectedReadAt =
                    persistedReadAt == null
                            ? now
                            : persistedReadAt;
        }

        LocalDateTime firstMessageAt =
                earliest(persistedMessageAt, detectedMessageAt);
        LocalDateTime firstReadAt =
                earliest(persistedReadAt, detectedReadAt);
        LocalDateTime firstReactionAt =
                earliest(firstMessageAt, firstReadAt);

        if (firstReactionAt == null) {
            return null;
        }

        String reason;
        if (firstMessageAt != null && firstReadAt != null) {
            reason = "The seller reacted to the current PENDING offer (read/message), first detected at "
                    + firstReactionAt;
        } else if (firstMessageAt != null) {
            reason = "The seller sent a normal chat message after the current offer, first detected at "
                    + firstReactionAt;
        } else {
            reason = "The seller read the current offer but left it PENDING, first detected at "
                    + firstReactionAt;
        }

        return new SellerReaction(firstReactionAt, reason);
    }

    private PendingNegotiationDecision decideUntouchedFinalStepPendingExpiry(
            ListingResponseDto listing,
            BotConfigurationDto configuration,
            LocalDateTime startedAt,
            LocalDateTime now
    ) {
        if (!isFinalNegotiationStep(listing, configuration)) {
            return null;
        }

        if (startedAt == null) {
            return PendingNegotiationDecision.waitForSeller(
                    "Vinted still reports the final negotiation step as PENDING, but currentStepStartedAt is missing. "
                            + "The bot cannot safely calculate the final-step expiry."
            );
        }

        LocalDateTime expiresAt =
                startedAt.plusHours(FINAL_STEP_PENDING_EXPIRY_HOURS);

        if (!now.isBefore(expiresAt)) {
            return PendingNegotiationDecision.expire(
                    "The untouched final negotiation step has remained PENDING for at least "
                            + FINAL_STEP_PENDING_EXPIRY_HOURS
                            + "h without a seller message, read signal or formal response. "
                            + "It started at " + startedAt
                            + " and expired at " + expiresAt + "."
            );
        }

        return PendingNegotiationDecision.waitForSeller(
                "Vinted still reports the untouched final negotiation step as PENDING. "
                        + "With no seller reaction, the bot waits up to "
                        + FINAL_STEP_PENDING_EXPIRY_HOURS
                        + "h. This step started at "
                        + startedAt
                        + " and will expire at "
                        + expiresAt + "."
        );
    }

    private int noResponseWaitHours(
            NegotiationStepDto currentStep
    ) {
        if (currentStep.getRejectionAction()
                == NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
                && currentStep.getRejectionWaitHours() != null
                && currentStep.getRejectionWaitHours() > 0) {
            return currentStep.getRejectionWaitHours();
        }

        return FALLBACK_NON_RESPONSE_WAIT_HOURS;
    }

    private NegotiationStepDto findCurrentStep(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (listing.currentStep() == null
                || configuration.getNegotiationSteps() == null) {
            throw new IllegalStateException(
                    "Cannot evaluate PENDING policy without a current negotiation step."
            );
        }

        return configuration.getNegotiationSteps()
                .stream()
                .filter(Objects::nonNull)
                .filter(step -> Objects.equals(
                        step.getStepNumber(),
                        listing.currentStep()
                ))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot find negotiation configuration for current step "
                                + listing.currentStep()
                ));
    }

    private NegotiationStepDto findNextStep(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (listing.currentStep() == null
                || configuration.getNegotiationSteps() == null) {
            return null;
        }

        return configuration.getNegotiationSteps()
                .stream()
                .filter(Objects::nonNull)
                .filter(step -> step.getStepNumber() != null)
                .filter(step -> step.getStepNumber() > listing.currentStep())
                .min(Comparator.comparing(
                        NegotiationStepDto::getStepNumber
                ))
                .orElse(null);
    }

    private boolean isFinalNegotiationStep(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (listing.currentStep() == null
                || configuration.getNegotiationSteps() == null
                || configuration.getNegotiationSteps().isEmpty()) {
            return false;
        }

        return configuration.getNegotiationSteps()
                .stream()
                .filter(Objects::nonNull)
                .map(NegotiationStepDto::getStepNumber)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(maxStep ->
                        Objects.equals(maxStep, listing.currentStep())
                )
                .orElse(false);
    }

    private LocalDateTime currentStepTimestamp(
            LocalDateTime candidate,
            LocalDateTime stepStartedAt
    ) {
        if (candidate == null) {
            return null;
        }
        if (stepStartedAt != null && candidate.isBefore(stepStartedAt)) {
            return null;
        }
        return candidate;
    }

    private LocalDateTime earliest(
            LocalDateTime left,
            LocalDateTime right
    ) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }

    private LocalDateTime parseDateTime(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(rawValue);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private record SellerReaction(
            LocalDateTime detectedAt,
            String reason
    ) {
    }
}

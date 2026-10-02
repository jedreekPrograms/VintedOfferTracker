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

/**
 * A visible Vinted PENDING state is authoritative: the seller has not formally
 * accepted, rejected or countered the latest offer yet.
 *
 * Reading the offer or sending a normal chat message is not a formal seller
 * response. For non-final steps we nevertheless avoid leaving a PENDING offer
 * open forever: the no-response timeout is twice the configured rejection wait
 * for the current step. If the rejection policy is immediate/legacy and has no
 * positive wait value, a conservative 12h fallback is used.
 *
 * The final configured step is never auto-raised again and expires after 48h.
 */
public class PendingNegotiationPolicy {

    private static final int FINAL_STEP_PENDING_EXPIRY_HOURS = 48;
    private static final int FALLBACK_NON_RESPONSE_WAIT_HOURS = 12;
    private static final int NON_RESPONSE_MULTIPLIER = 2;

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
        Objects.requireNonNull(activitySnapshot, "Conversation activity snapshot cannot be null");
        Objects.requireNonNull(configuration, "Bot configuration cannot be null");

        PendingNegotiationDecision finalStepDecision =
                decideFinalStepPendingExpiry(listing, configuration);
        if (finalStepDecision != null) {
            return finalStepDecision;
        }

        PendingNegotiationDecision nonFinalTimeoutDecision =
                decideNonFinalPendingTimeout(listing, configuration);
        if (nonFinalTimeoutDecision != null) {
            return nonFinalTimeoutDecision;
        }

        if (activitySnapshot.sellerMessageAfterLatestOwnOffer()) {
            return PendingNegotiationDecision.waitForSeller(
                    "Vinted still reports the latest offer as PENDING. The seller sent a normal chat message, "
                            + "but no formal rejection, acceptance or counteroffer was detected, so the bot will not raise its price."
            );
        }

        if (activitySnapshot.readIndicatorAfterLatestOwnOffer()
                || hasTimestamp(listing.readDetectedAt())) {
            return PendingNegotiationDecision.waitForSeller(
                    "Vinted still reports the latest offer as PENDING. A read indicator exists, "
                            + "but reading an offer is not a formal response, so the bot will not raise its price."
            );
        }

        LocalDateTime startedAt = parseDateTime(listing.currentStepStartedAt());
        if (startedAt != null) {
            NegotiationStepDto currentStep = findCurrentStep(
                    listing,
                    configuration
            );
            int rejectionWaitHours = rejectionWaitHours(currentStep);
            long noResponseWaitHours =
                    (long) rejectionWaitHours * NON_RESPONSE_MULTIPLIER;
            LocalDateTime nextActionAt = startedAt.plusHours(
                    noResponseWaitHours
            );

            return PendingNegotiationDecision.waitForSeller(
                    "Vinted still reports the latest offer as PENDING since "
                            + startedAt
                            + ". No formal response exists yet. The non-final no-response timeout is "
                            + noResponseWaitHours
                            + "h (2x rejection wait), so the next step becomes eligible at "
                            + nextActionAt + "."
            );
        }

        return PendingNegotiationDecision.waitForSeller(
                "Vinted still reports the latest offer as PENDING. No trustworthy formal response exists, "
                        + "so the negotiation remains active."
        );
    }

    private PendingNegotiationDecision decideNonFinalPendingTimeout(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        NegotiationStepDto currentStep = findCurrentStep(
                listing,
                configuration
        );

        LocalDateTime startedAt = parseDateTime(
                listing.currentStepStartedAt()
        );

        if (startedAt == null) {
            return null;
        }

        int rejectionWaitHours = rejectionWaitHours(currentStep);
        long noResponseWaitHours =
                (long) rejectionWaitHours * NON_RESPONSE_MULTIPLIER;

        LocalDateTime nextActionAt = startedAt.plusHours(
                noResponseWaitHours
        );
        LocalDateTime now = LocalDateTime.now(clock);

        if (now.isBefore(nextActionAt)) {
            return null;
        }

        NegotiationStepDto configuredNextStep = findNextStep(
                listing,
                configuration
        );

        if (configuredNextStep == null) {
            return PendingNegotiationDecision.expire(
                    "Vinted still reports the offer as PENDING after "
                            + noResponseWaitHours
                            + "h without a formal seller response, but no next configured negotiation step exists."
            );
        }

        return pricingService
                .adaptNextStep(
                        listing,
                        configuredNextStep,
                        configuration
                )
                .map(nextStep ->
                        PendingNegotiationDecision.sendNextStep(
                                nextStep,
                                "Vinted still reports step "
                                        + listing.currentStep()
                                        + " as PENDING without a formal seller response. "
                                        + "The no-response policy waits 2x the rejection wait: "
                                        + rejectionWaitHours + "h x "
                                        + NON_RESPONSE_MULTIPLIER + " = "
                                        + noResponseWaitHours + "h. "
                                        + "Step started at " + startedAt
                                        + "; next step became eligible at "
                                        + nextActionAt + "."
                        )
                )
                .orElseGet(() ->
                        PendingNegotiationDecision.expire(
                                "Vinted still reports the offer as PENDING after "
                                        + noResponseWaitHours
                                        + "h, but the next adaptive step cannot be sent within the configured automatic-offer cap. "
                                        + "The stale negotiation is closed instead of remaining active forever."
                        )
                );
    }

    private int rejectionWaitHours(
            NegotiationStepDto currentStep
    ) {
        if (currentStep.getRejectionAction()
                == NegotiationReactionAction.WAIT_BEFORE_NEXT_STEP
                && currentStep.getRejectionWaitHours() != null
                && currentStep.getRejectionWaitHours() > 0) {
            return currentStep.getRejectionWaitHours();
        }

        return FALLBACK_NON_RESPONSE_WAIT_HOURS / NON_RESPONSE_MULTIPLIER;
    }

    private NegotiationStepDto findCurrentStep(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (listing.currentStep() == null
                || configuration.getNegotiationSteps() == null) {
            throw new IllegalStateException(
                    "Cannot evaluate PENDING timeout without a current negotiation step."
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

    private PendingNegotiationDecision decideFinalStepPendingExpiry(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (!isFinalNegotiationStep(listing, configuration)) {
            return null;
        }

        LocalDateTime startedAt = parseDateTime(listing.currentStepStartedAt());
        if (startedAt == null) {
            return null;
        }

        LocalDateTime expiresAt = startedAt.plusHours(
                FINAL_STEP_PENDING_EXPIRY_HOURS
        );
        LocalDateTime now = LocalDateTime.now(clock);

        if (!now.isBefore(expiresAt)) {
            return PendingNegotiationDecision.expire(
                    "The final negotiation step has remained PENDING for at least "
                            + FINAL_STEP_PENDING_EXPIRY_HOURS
                            + "h without a formal acceptance, rejection or counteroffer. "
                            + "It started at " + startedAt
                            + " and expired at " + expiresAt + "."
            );
        }

        return PendingNegotiationDecision.waitForSeller(
                "Vinted still reports the final negotiation step as PENDING. "
                        + "The bot waits up to "
                        + FINAL_STEP_PENDING_EXPIRY_HOURS
                        + "h for a formal response. This step started at "
                        + startedAt
                        + " and will expire at " + expiresAt + "."
        );
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

        return configuration.getNegotiationSteps().stream()
                .filter(Objects::nonNull)
                .map(step -> step.getStepNumber())
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .map(maxStep -> Objects.equals(maxStep, listing.currentStep()))
                .orElse(false);
    }

    private boolean hasTimestamp(String rawValue) {
        return parseDateTime(rawValue) != null;
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
}
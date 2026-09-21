package pl.flipbot.playwright.negotiation;

import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Objects;

/**
 * A visible Vinted PENDING state is authoritative: the seller has not formally
 * accepted, rejected or countered the latest offer yet.
 *
 * Reading the offer, sending a normal chat message, or simply letting time pass
 * is not enough evidence to raise our price or close the negotiation. Formal
 * rejection/counteroffer timing is handled by NegotiationDecisionService using
 * the user-configured response policies.
 */
public class PendingNegotiationPolicy {

    private static final int FINAL_STEP_PENDING_EXPIRY_HOURS = 48;

    private final Clock clock;

    public PendingNegotiationPolicy() {
        this(Clock.systemDefaultZone());
    }

    PendingNegotiationPolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
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
            return PendingNegotiationDecision.waitForSeller(
                    "Vinted still reports the latest offer as PENDING since "
                            + startedAt
                            + ". Elapsed time alone is not terminal evidence, so the negotiation remains active."
            );
        }

        return PendingNegotiationDecision.waitForSeller(
                "Vinted still reports the latest offer as PENDING. No trustworthy formal response exists, "
                        + "so the negotiation remains active."
        );
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
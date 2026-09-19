package pl.flipbot.playwright.negotiation;

import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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

    public PendingNegotiationDecision decide(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot,
            BotConfigurationDto configuration
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(activitySnapshot, "Conversation activity snapshot cannot be null");
        Objects.requireNonNull(configuration, "Bot configuration cannot be null");

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

package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * A read receipt or a non-price seller message is engagement, not a
 * concession. PENDING therefore never raises our own price. Price movement is
 * reserved for a formal rejection or a concrete seller counteroffer.
 */
@Slf4j
public class PendingNegotiationPolicy {

    private static final long NO_ENGAGEMENT_EXPIRY_HOURS = 48L;
    private static final long SELLER_MESSAGE_GRACE_HOURS = 24L;

    public PendingNegotiationDecision decide(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot,
            BotConfigurationDto configuration
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");
        Objects.requireNonNull(activitySnapshot, "Conversation activity snapshot cannot be null");
        Objects.requireNonNull(configuration, "Bot configuration cannot be null");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime currentStepStartedAt = parseDateTime(
                listing.currentStepStartedAt(), "currentStepStartedAt", listing
        );

        if (currentStepStartedAt == null) {
            return PendingNegotiationDecision.waitForSeller(
                    "The latest offer is pending and currentStepStartedAt is missing, so inactivity cannot be evaluated safely. The bot will not raise its own price."
            );
        }

        LocalDateTime baseExpiry = currentStepStartedAt.plusHours(
                NO_ENGAGEMENT_EXPIRY_HOURS
        );
        LocalDateTime sellerActivityAt = resolveSellerActivityAt(
                listing, activitySnapshot, currentStepStartedAt
        );

        if (sellerActivityAt != null) {
            LocalDateTime messageGraceExpiry = sellerActivityAt.plusHours(
                    SELLER_MESSAGE_GRACE_HOURS
            );
            LocalDateTime expiryAt = messageGraceExpiry.isAfter(baseExpiry)
                    ? messageGraceExpiry
                    : baseExpiry;

            if (now.isBefore(expiryAt)) {
                return PendingNegotiationDecision.waitForSeller(
                        "The seller sent a normal message at " + sellerActivityAt
                                + ", but did not reject, accept or name a price. The bot will not bid against itself. Conversation expiry is "
                                + expiryAt + "."
                );
            }
            return PendingNegotiationDecision.expire(
                    "The seller engaged without a formal price response, and the later of the 48h offer window and 24h message grace period elapsed. No self-concession was sent."
            );
        }

        LocalDateTime readDetectedAt = resolveReadDetectedAt(
                listing, activitySnapshot, currentStepStartedAt, now
        );
        if (readDetectedAt != null) {
            if (now.isBefore(baseExpiry)) {
                return PendingNegotiationDecision.waitForSeller(
                        "The latest offer was read at " + readDetectedAt
                                + ", but the seller did not make a price move. The bot keeps the same offer instead of rewarding silence. Inactivity expiry is "
                                + baseExpiry + "."
                );
            }
            return PendingNegotiationDecision.expire(
                    "The latest offer was read but received no acceptance, rejection or counteroffer for 48 hours. No self-concession was sent."
            );
        }

        if (now.isBefore(baseExpiry)) {
            return PendingNegotiationDecision.waitForSeller(
                    "The latest offer is pending with no seller message and no read indicator. The inactivity timeout is "
                            + baseExpiry + "."
            );
        }

        return PendingNegotiationDecision.expire(
                "The latest offer received no seller message, no read indicator and no formal response for at least "
                        + NO_ENGAGEMENT_EXPIRY_HOURS + " hours."
        );
    }

    private LocalDateTime resolveSellerActivityAt(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot,
            LocalDateTime currentStepStartedAt
    ) {
        LocalDateTime persisted = parseDateTime(
                listing.sellerActivityAt(), "sellerActivityAt", listing
        );
        LocalDateTime detected = activitySnapshot.sellerMessageAfterLatestOwnOffer()
                ? activitySnapshot.latestSellerMessageAt()
                : null;

        persisted = ignoreIfOlderThanCurrentStep(
                persisted, currentStepStartedAt, "persisted seller activity", listing
        );
        detected = ignoreIfOlderThanCurrentStep(
                detected, currentStepStartedAt, "detected seller message", listing
        );

        if (persisted == null) return detected;
        if (detected == null) return persisted;
        return detected.isAfter(persisted) ? detected : persisted;
    }

    private LocalDateTime resolveReadDetectedAt(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot,
            LocalDateTime currentStepStartedAt,
            LocalDateTime now
    ) {
        LocalDateTime persisted = parseDateTime(
                listing.readDetectedAt(), "readDetectedAt", listing
        );
        persisted = ignoreIfOlderThanCurrentStep(
                persisted, currentStepStartedAt, "persisted read detection", listing
        );
        if (persisted != null) return persisted;
        return activitySnapshot.readIndicatorAfterLatestOwnOffer() ? now : null;
    }

    private LocalDateTime ignoreIfOlderThanCurrentStep(
            LocalDateTime activityAt,
            LocalDateTime currentStepStartedAt,
            String activityName,
            ListingResponseDto listing
    ) {
        if (activityAt == null || currentStepStartedAt == null) return activityAt;
        if (activityAt.isBefore(currentStepStartedAt)) {
            log.debug(
                    "[PENDING POLICY] Ignoring {} at {} for marketplace listing {} because current step {} started at {}.",
                    activityName, activityAt, listing.listingId(),
                    listing.currentStep(), currentStepStartedAt
            );
            return null;
        }
        return activityAt;
    }

    private LocalDateTime parseDateTime(
            String rawValue,
            String fieldName,
            ListingResponseDto listing
    ) {
        if (rawValue == null || rawValue.isBlank()) return null;
        try {
            return LocalDateTime.parse(rawValue);
        } catch (DateTimeParseException exception) {
            log.warn(
                    "[PENDING POLICY] Could not parse {}={} for backend listing {}, marketplace listing {}. This timestamp will be ignored.",
                    fieldName, rawValue, listing.id(), listing.listingId()
            );
            return null;
        }
    }
}

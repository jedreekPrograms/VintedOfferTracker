package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.NegotiationStepDto;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
public class PendingNegotiationPolicy {

    private static final long SELLER_MESSAGE_WAIT_HOURS = 3L;
    private static final int DEFAULT_READ_WAIT_HOURS = 3;
    private static final int DEFAULT_UNREAD_WAIT_HOURS = 48;

    private final AdaptiveNegotiationPricingService pricingService =
            new AdaptiveNegotiationPricingService();

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
                listing.currentStepStartedAt(),
                "currentStepStartedAt",
                listing
        );

        NegotiationStepDto currentStep = findCurrentStep(
                listing.currentStep(),
                configuration.getNegotiationSteps()
        ).orElse(null);

        int readWaitHours = configuredDelay(
                currentStep == null ? null : currentStep.getReadWaitHours(),
                DEFAULT_READ_WAIT_HOURS,
                "readWaitHours",
                listing
        );
        int unreadWaitHours = configuredDelay(
                currentStep == null ? null : currentStep.getUnreadWaitHours(),
                DEFAULT_UNREAD_WAIT_HOURS,
                "unreadWaitHours",
                listing
        );

        LocalDateTime sellerActivityAt = resolveSellerActivityAt(
                listing,
                activitySnapshot,
                currentStepStartedAt
        );

        if (sellerActivityAt != null) {
            LocalDateTime nextActionAt = sellerActivityAt.plusHours(
                    SELLER_MESSAGE_WAIT_HOURS
            );

            if (now.isBefore(nextActionAt)) {
                return PendingNegotiationDecision.waitForSeller(
                        "The seller sent a normal message at "
                                + sellerActivityAt
                                + ". The bot will wait until "
                                + nextActionAt
                                + " before continuing the negotiation."
                );
            }

            return continueOrExpire(
                    listing,
                    configuration,
                    "The seller sent a normal message at "
                            + sellerActivityAt
                            + " and at least "
                            + SELLER_MESSAGE_WAIT_HOURS
                            + " hours have passed without a formal acceptance, rejection or counteroffer."
            );
        }

        LocalDateTime readDetectedAt = resolveReadDetectedAt(
                listing,
                activitySnapshot,
                currentStepStartedAt,
                now
        );

        if (readDetectedAt != null) {
            LocalDateTime nextActionAt = readDetectedAt.plusHours(readWaitHours);

            if (now.isBefore(nextActionAt)) {
                return PendingNegotiationDecision.waitForSeller(
                        "The latest offer has been read. The read timer started at "
                                + readDetectedAt
                                + ". Step "
                                + listing.currentStep()
                                + " is configured to wait "
                                + readWaitHours
                                + " hour(s), so the bot will wait until "
                                + nextActionAt
                                + " before continuing the negotiation."
                );
            }

            return continueOrExpire(
                    listing,
                    configuration,
                    "The latest offer has been read and the configured "
                            + readWaitHours
                            + " hour read follow-up delay for step "
                            + listing.currentStep()
                            + " has elapsed without a formal acceptance, rejection or counteroffer."
            );
        }

        if (currentStepStartedAt == null) {
            return PendingNegotiationDecision.waitForSeller(
                    "The latest offer is pending and the backend has no currentStepStartedAt timestamp, so the configured "
                            + unreadWaitHours
                            + " hour unread timer cannot be evaluated safely."
            );
        }

        LocalDateTime nextUnreadActionAt = currentStepStartedAt.plusHours(
                unreadWaitHours
        );

        if (now.isBefore(nextUnreadActionAt)) {
            return PendingNegotiationDecision.waitForSeller(
                    "The latest offer is pending with no seller message and no read indicator. Step "
                            + listing.currentStep()
                            + " is configured to wait "
                            + unreadWaitHours
                            + " hour(s); the next action may happen at "
                            + nextUnreadActionAt
                            + "."
            );
        }

        return continueOrExpire(
                listing,
                configuration,
                "The latest offer received no seller message, no read indicator and no formal response for the configured "
                        + unreadWaitHours
                        + " hour unread delay on step "
                        + listing.currentStep()
                        + "."
        );
    }

    private PendingNegotiationDecision continueOrExpire(
            ListingResponseDto listing,
            BotConfigurationDto configuration,
            String activityReason
    ) {
        Optional<NegotiationStepDto> configuredNextStep = findNextStep(
                listing.currentStep(),
                configuration.getNegotiationSteps()
        );

        if (configuredNextStep.isEmpty()) {
            return PendingNegotiationDecision.expire(
                    activityReason
                            + " There are no more automated negotiation steps, so the conversation will be closed as EXPIRED."
            );
        }

        Optional<NegotiationStepDto> effectiveNextStep =
                pricingService.adaptNextStep(
                        listing,
                        configuredNextStep.get(),
                        configuration
                );

        if (effectiveNextStep.isPresent()) {
            return PendingNegotiationDecision.sendNextStep(
                    effectiveNextStep.get(),
                    activityReason
                            + " Another negotiation step is available. Effective next offer: "
                            + effectiveNextStep.get().getOfferPrice()
                            + "."
            );
        }

        return PendingNegotiationDecision.expire(
                activityReason
                        + " Another configured step exists, but its adaptive price would exceed the global negotiation cap "
                        + configuration.getMaxAutomaticOffer()
                        + ", so no higher automatic offer will be sent and the conversation will be closed as EXPIRED."
        );
    }

    private Optional<NegotiationStepDto> findCurrentStep(
            Integer currentStepNumber,
            List<NegotiationStepDto> negotiationSteps
    ) {
        if (currentStepNumber == null || negotiationSteps == null) {
            return Optional.empty();
        }
        return negotiationSteps.stream()
                .filter(Objects::nonNull)
                .filter(step -> Objects.equals(step.getStepNumber(), currentStepNumber))
                .findFirst();
    }

    private int configuredDelay(
            Integer configured,
            int fallback,
            String fieldName,
            ListingResponseDto listing
    ) {
        if (configured == null) {
            log.debug(
                    "[PENDING POLICY] Step {} has no {} in the transport payload for marketplace listing {}. Using backward-compatible default {}h.",
                    listing.currentStep(),
                    fieldName,
                    listing.listingId(),
                    fallback
            );
            return fallback;
        }
        if (configured < 1) {
            throw new IllegalStateException(
                    "Negotiation step " + listing.currentStep()
                            + " has invalid " + fieldName + "=" + configured
            );
        }
        return configured;
    }

    private LocalDateTime resolveSellerActivityAt(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot,
            LocalDateTime currentStepStartedAt
    ) {
        LocalDateTime persistedSellerActivityAt = parseDateTime(
                listing.sellerActivityAt(),
                "sellerActivityAt",
                listing
        );

        LocalDateTime detectedSellerActivityAt =
                activitySnapshot.sellerMessageAfterLatestOwnOffer()
                        ? activitySnapshot.latestSellerMessageAt()
                        : null;

        persistedSellerActivityAt = ignoreIfOlderThanCurrentStep(
                persistedSellerActivityAt,
                currentStepStartedAt,
                "persisted seller activity",
                listing
        );

        detectedSellerActivityAt = ignoreIfOlderThanCurrentStep(
                detectedSellerActivityAt,
                currentStepStartedAt,
                "detected seller message",
                listing
        );

        if (persistedSellerActivityAt == null) {
            return detectedSellerActivityAt;
        }

        if (detectedSellerActivityAt == null) {
            return persistedSellerActivityAt;
        }

        return detectedSellerActivityAt.isAfter(persistedSellerActivityAt)
                ? detectedSellerActivityAt
                : persistedSellerActivityAt;
    }

    private LocalDateTime resolveReadDetectedAt(
            ListingResponseDto listing,
            ConversationActivitySnapshot activitySnapshot,
            LocalDateTime currentStepStartedAt,
            LocalDateTime now
    ) {
        LocalDateTime persistedReadDetectedAt = parseDateTime(
                listing.readDetectedAt(),
                "readDetectedAt",
                listing
        );

        persistedReadDetectedAt = ignoreIfOlderThanCurrentStep(
                persistedReadDetectedAt,
                currentStepStartedAt,
                "persisted read detection",
                listing
        );

        if (persistedReadDetectedAt != null) {
            return persistedReadDetectedAt;
        }

        if (activitySnapshot.readIndicatorAfterLatestOwnOffer()) {
            return now;
        }

        return null;
    }

    private LocalDateTime ignoreIfOlderThanCurrentStep(
            LocalDateTime activityAt,
            LocalDateTime currentStepStartedAt,
            String activityName,
            ListingResponseDto listing
    ) {
        if (activityAt == null || currentStepStartedAt == null) {
            return activityAt;
        }

        if (activityAt.isBefore(currentStepStartedAt)) {
            log.debug(
                    "[PENDING POLICY] Ignoring {} at {} for marketplace listing {} because current step {} started at {}.",
                    activityName,
                    activityAt,
                    listing.listingId(),
                    listing.currentStep(),
                    currentStepStartedAt
            );
            return null;
        }

        return activityAt;
    }

    private Optional<NegotiationStepDto> findNextStep(
            Integer currentStepNumber,
            List<NegotiationStepDto> negotiationSteps
    ) {
        if (currentStepNumber == null) {
            throw new IllegalStateException("Negotiating listing has no current step");
        }

        if (negotiationSteps == null || negotiationSteps.isEmpty()) {
            throw new IllegalStateException("Bot configuration has no negotiation steps");
        }

        return negotiationSteps.stream()
                .filter(Objects::nonNull)
                .filter(step -> step.getStepNumber() != null)
                .filter(step -> step.getStepNumber() > currentStepNumber)
                .min(Comparator.comparing(NegotiationStepDto::getStepNumber))
                .map(this::validateNextStep);
    }

    private NegotiationStepDto validateNextStep(NegotiationStepDto nextStep) {
        if (nextStep.getOfferPrice() == null) {
            throw new IllegalStateException(
                    "Negotiation step "
                            + nextStep.getStepNumber()
                            + " has no offer price"
            );
        }

        if (nextStep.getOfferPrice().signum() <= 0) {
            throw new IllegalStateException(
                    "Negotiation step "
                            + nextStep.getStepNumber()
                            + " has an invalid offer price: "
                            + nextStep.getOfferPrice()
            );
        }

        return nextStep;
    }

    private LocalDateTime parseDateTime(
            String rawValue,
            String fieldName,
            ListingResponseDto listing
    ) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(rawValue);
        } catch (DateTimeParseException exception) {
            log.warn(
                    "[PENDING POLICY] Could not parse {}={} for backend listing {}, marketplace listing {}. This timestamp will be ignored.",
                    fieldName,
                    rawValue,
                    listing.id(),
                    listing.listingId()
            );
            log.trace(
                    "[PENDING POLICY] Full timestamp parse exception.",
                    exception
            );
            return null;
        }
    }
}

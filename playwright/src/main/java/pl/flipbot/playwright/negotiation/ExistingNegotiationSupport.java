package pl.flipbot.playwright.negotiation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.listing.NegotiationActivityClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.NegotiationActivityRequestDto;
import pl.flipbot.playwright.api.listing.dto.UpdateListingRequestDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.target.ListingTargetAssessment;
import pl.flipbot.playwright.target.ListingTargetMatcher;





@Slf4j
@RequiredArgsConstructor
public class ExistingNegotiationSupport {

    private static final String VINTED_MODEL = "VINTED_MODEL";
    private static final int FRIENDLY_ERROR_MAX_LENGTH = 500;

    private final BotContext context;
    private final ListingClient listingClient;
    private final ListingStatusUpdater listingStatusUpdater;
    private final NegotiationActivityClient negotiationActivityClient;

    private final ListingTargetMatcher listingTargetMatcher =
            new ListingTargetMatcher();

    public void logConversationActivity(
            ListingResponseDto listing,
            ConversationActivitySnapshot activity
    ) {
        if (!activity.inspectionSucceeded()) {
            log.debug(
                    "[CONVERSATION ACTIVITY] Inspection unavailable for listing {}.",
                    listing.listingId()
            );
            return;
        }
        if (!activity.latestOwnOfferFound()) {
            return;
        }
        if (activity.sellerMessageAfterLatestOwnOffer()) {
            log.info(
                    "[CONVERSATION ACTIVITY] Listing {} has seller message after latest own offer at {}: {}",
                    listing.listingId(),
                    activity.latestSellerMessageAt(),
                    abbreviate(activity.latestSellerMessageText(), 160)
            );
        }
        if (activity.readIndicatorAfterLatestOwnOffer()) {
            log.info(
                    "[CONVERSATION ACTIVITY] Listing {} shows read indicator after latest own offer.",
                    listing.listingId()
            );
        }
    }

    public void persistConversationActivity(
            ListingResponseDto listing,
            ConversationActivitySnapshot activity,
            NegotiationConversationSnapshot snapshot
    ) {
        String formalResponseFingerprint = NegotiationResponseFingerprint.create(
                listing,
                snapshot
        );

        boolean sellerActivity = activity.inspectionSucceeded()
                && activity.latestOwnOfferFound()
                && activity.sellerMessageAfterLatestOwnOffer()
                && activity.latestSellerMessageAt() != null;
        boolean readDetected = activity.inspectionSucceeded()
                && activity.latestOwnOfferFound()
                && activity.readIndicatorAfterLatestOwnOffer();

        if (!sellerActivity
                && !readDetected
                && formalResponseFingerprint == null) {
            return;
        }

        try {
            negotiationActivityClient.recordActivity(
                    context.getBot().getId(),
                    listing.id(),
                    new NegotiationActivityRequestDto(
                            sellerActivity ? activity.latestSellerMessageAt() : null,
                            readDetected,
                            formalResponseFingerprint
                    )
            );
        } catch (Exception exception) {
            log.warn(
                    "[NEGOTIATION ACTIVITY API] Could not persist activity/response timer for listing {}: {}",
                    listing.listingId(),
                    friendlyError(exception)
            );
        }
    }

    public boolean matchesConfiguredTarget(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        if (configuration == null) {
            throw new IllegalArgumentException("Bot configuration cannot be null");
        }
        if (listing == null) {
            return false;
        }

        String targetMode = configuration.getTargetMode();
        if (targetMode == null
                || targetMode.isBlank()
                || VINTED_MODEL.equalsIgnoreCase(targetMode.trim())) {
            /*
             * Native Vinted model-filter mode is authoritative by design.
             * Existing conversations must never be stopped because a seller
             * title/URL looks generic or contradictory after the negotiation
             * has already been started from that exact native filter.
             */
            log.debug(
                    "[TARGET GUARD] Skipping post-filter target guard for existing VINTED_MODEL negotiation {}.",
                    listing.listingId()
            );
            return true;
        }

        /*
         * SEARCH_QUERY remains guarded. For historical conversations we stop
         * only on a conclusive mismatch; ambiguous stored text is not enough to
         * terminate an already-started conversation.
         */
        ListingTargetAssessment assessment =
                listingTargetMatcher.assessCatalogListing(
                        listing,
                        configuration
                );

        if (assessment == ListingTargetAssessment.MISMATCH) {
            log.error(
                    "[TARGET GUARD] Existing SEARCH_QUERY negotiation {} has a conclusive target mismatch. query='{}', title='{}'.",
                    listing.listingId(),
                    configuration.getSearchQuery(),
                    listing.title()
            );
            return false;
        }

        return true;
    }

    public ListingResponseDto finishWrongTargetNegotiation(
            ListingResponseDto listing
    ) {
        if (listing.currentStep() == null || listing.currentStep() <= 0) {
            throw new IllegalStateException(
                    "Invalid current step for wrong-target listing " + listing.id()
            );
        }
        if (listing.currentPrice() == null && listing.originalPrice() == null) {
            throw new IllegalStateException(
                    "Missing price for wrong-target listing " + listing.id()
            );
        }

        ListingResponseDto updated = listingClient.updateListing(
                context.getBot().getId(),
                listing.id(),
                new UpdateListingRequestDto(
                        "FINISHED",
                        listing.currentPrice() != null
                                ? listing.currentPrice()
                                : listing.originalPrice(),
                        listing.currentStep(),
                        false,
                        listing.conversationId(),
                        listing.conversationUrl()
                )
        );

        if (!"FINISHED".equals(updated.status())
                || Boolean.TRUE.equals(updated.awaitingSellerResponse())) {
            throw new IllegalStateException(
                    "Backend returned invalid state while finishing wrong-target listing "
                            + listing.id()
            );
        }
        return updated;
    }

    public String friendlyError(Throwable exception) {
        if (exception == null) {
            return "Unknown error";
        }

        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        String singleLine = message
                .replaceAll("\\s+", " ")
                .trim();

        if (singleLine.length() <= FRIENDLY_ERROR_MAX_LENGTH) {
            return singleLine;
        }

        return singleLine.substring(0, FRIENDLY_ERROR_MAX_LENGTH) + "...";
    }

    private String abbreviate(String value, int max) {
        if (value == null) {
            return "<none>";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= max
                ? normalized
                : normalized.substring(0, max) + "...";
    }

}

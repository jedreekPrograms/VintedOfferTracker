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

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

@Slf4j
@RequiredArgsConstructor
public class ExistingNegotiationSupport {

    private final BotContext context;
    private final ListingClient listingClient;
    private final ListingStatusUpdater listingStatusUpdater;
    private final NegotiationActivityClient negotiationActivityClient;

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
        String formalResponseFingerprint = formalResponseFingerprint(
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
            /*
             * A delayed response rule must fail closed if we cannot persist its
             * first-detection time. The decision layer sees no matching stable
             * timestamp and keeps waiting instead of guessing.
             */
            log.warn(
                    "[NEGOTIATION ACTIVITY API] Could not persist activity/response timer for listing {}: {}",
                    listing.listingId(),
                    friendlyError(exception)
            );
        }
    }

    public String formalResponseFingerprint(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot
    ) {
        if (listing == null || snapshot == null || listing.currentStep() == null) {
            return null;
        }

        return switch (snapshot.result()) {
            case REJECTED -> "REJECTED:" + listing.currentStep();
            case SELLER_COUNTER_OFFER -> {
                BigDecimal price = snapshot.sellerCounterOfferPrice();
                if (price == null) {
                    yield null;
                }
                yield "COUNTER:"
                        + listing.currentStep()
                        + ":"
                        + price.stripTrailingZeros().toPlainString();
            }
            default -> null;
        };
    }

    public boolean matchesConfiguredTarget(
            ListingResponseDto listing,
            BotConfigurationDto configuration
    ) {
        String model = normalize(configuration.getModel());
        if (model.isBlank()) {
            return true;
        }

        String brand = normalize(configuration.getBrand());
        String expected = brand.isBlank()
                || model.equals(brand)
                || model.startsWith(brand + " ")
                ? model
                : brand + " " + model;

        String actual = normalize(listing.title());
        boolean matches = expected.equals(actual);

        if (!matches) {
            log.error(
                    "[TARGET GUARD] Listing {} target mismatch. Expected='{}', actual='{}'.",
                    listing.listingId(),
                    expected,
                    actual
            );
        }
        return matches;
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
        int newline = message.indexOf('\n');
        return newline > 0
                ? message.substring(0, newline).trim()
                : message.trim();
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

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String prepared = value.replace("+", " plus ").replace("＋", " plus ");
        String withoutDiacritics = Normalizer.normalize(
                        prepared,
                        Normalizer.Form.NFD
                )
                .replaceAll("\\p{M}+", "");
        return withoutDiacritics
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }
}

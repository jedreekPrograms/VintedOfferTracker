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
import pl.flipbot.playwright.target.VintedModelTargetGuard;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
public class ExistingNegotiationSupport {

    private static final String VINTED_MODEL = "VINTED_MODEL";
    private static final int FRIENDLY_ERROR_MAX_LENGTH = 500;

    private final BotContext context;
    private final ListingClient listingClient;
    private final ListingStatusUpdater listingStatusUpdater;
    private final NegotiationActivityClient negotiationActivityClient;

    private final VintedModelTargetGuard vintedModelTargetGuard =
            new VintedModelTargetGuard();

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
             * Existing negotiations are historical business state, so we do
             * not require positive model proof again and risk terminating a
             * legitimate conversation because of a generic seller title.
             *
             * We DO stop follow-ups when the stored title or URL contains
             * conclusive evidence of a different model. This prevents a legacy
             * wrong-target negotiation from sending any additional steps.
             */
            Optional<String> titleMismatch =
                    vintedModelTargetGuard.findConclusiveMismatch(
                            configuration.getModel(),
                            listing.title()
                    );

            if (titleMismatch.isPresent()) {
                log.error(
                        "[TARGET GUARD] Existing VINTED_MODEL negotiation {} is a conclusive wrong target from stored title. Configured='{}', title='{}'. Reason: {}",
                        listing.listingId(),
                        configuration.getModel(),
                        listing.title(),
                        titleMismatch.get()
                );
                return false;
            }

            Optional<String> urlMismatch =
                    vintedModelTargetGuard.findConclusiveMismatch(
                            configuration.getModel(),
                            listing.url()
                    );

            if (urlMismatch.isPresent()) {
                log.error(
                        "[TARGET GUARD] Existing VINTED_MODEL negotiation {} is a conclusive wrong target from stored URL. Configured='{}', url='{}'. Reason: {}",
                        listing.listingId(),
                        configuration.getModel(),
                        listing.url(),
                        urlMismatch.get()
                );
                return false;
            }

            log.debug(
                    "[TARGET GUARD] Existing VINTED_MODEL negotiation {} has no conclusive conflicting model evidence. It may continue.",
                    listing.listingId()
            );
            return true;
        }

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

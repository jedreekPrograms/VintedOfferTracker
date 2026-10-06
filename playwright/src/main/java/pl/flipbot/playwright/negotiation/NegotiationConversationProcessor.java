package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.privatecore.PrivateVintedCoreBridge;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.util.Objects;

@Slf4j
@RequiredArgsConstructor
public class NegotiationConversationProcessor {

    private final BotContext context;

    private final HumanVerificationHandler humanVerificationHandler =
            new HumanVerificationHandler();

    /*
     * Compatibility method retained for the older worker entry point.
     */
    public NegotiationConversationResult inspect(
            ListingResponseDto listing
    ) {
        NegotiationConversationSnapshot snapshot =
                inspectSnapshot(listing);

        if (snapshot.result()
                == NegotiationConversationResult.SELLER_COUNTER_OFFER) {
            log.warn(
                    "[CONVERSATION] Seller counteroffer {} was detected for listing {}, but the compatibility API cannot expose its price.",
                    snapshot.sellerCounterOfferPrice(),
                    listing.listingId()
            );
            return NegotiationConversationResult.UNKNOWN;
        }

        return snapshot.result();
    }

    public NegotiationConversationSnapshot inspectRecoverySnapshot(
            ListingResponseDto listing
    ) {
        Objects.requireNonNull(
                listing,
                "Listing cannot be null"
        );

        validateRecoveryListing(listing);

        Page page = context.getPage();

        log.info(
                "[NEGOTIATION RECOVERY] Opening terminal conversation {} for backend listing {}, marketplace listing {}",
                listing.conversationId(),
                listing.id(),
                listing.listingId()
        );

        new MarketplaceNavigator(context).goToTrustedVintedUrl(
                listing.conversationUrl()
        );

        humanVerificationHandler.waitUntilVerified(page);

        verifyOpenedConversationReadOnly(
                page,
                listing
        );

        NegotiationConversationSnapshot snapshot =
                inspectWithPrivateCore(
                        page,
                        listing
                );

        logSnapshot(
                listing,
                snapshot
        );

        return snapshot;
    }

    public NegotiationConversationSnapshot inspectSnapshot(
            ListingResponseDto listing
    ) {
        Objects.requireNonNull(
                listing,
                "Listing cannot be null"
        );

        validateListing(listing);

        Page page = context.getPage();

        log.info(
                "[CONVERSATION] Opening conversation {} for backend listing {}, marketplace listing {}",
                listing.conversationId(),
                listing.id(),
                listing.listingId()
        );

        new MarketplaceNavigator(context).goToTrustedVintedUrl(
                listing.conversationUrl()
        );

        humanVerificationHandler.waitUntilVerified(page);

        ListingResponseDto canonicalListing =
                validateOpenedConversation(
                        page,
                        listing
                );

        NegotiationConversationSnapshot snapshot =
                inspectWithPrivateCore(
                        page,
                        canonicalListing
                );

        logSnapshot(
                canonicalListing,
                snapshot
        );

        return snapshot;
    }

    private NegotiationConversationSnapshot inspectWithPrivateCore(
            Page page,
            ListingResponseDto listing
    ) {
        return PrivateVintedCoreBridge.inspectConversation(
                page,
                listing.listingId(),
                listing.conversationId(),
                listing.originalPrice(),
                () -> humanVerificationHandler
                        .waitUntilVerified(page)
        );
    }

    private void logSnapshot(
            ListingResponseDto listing,
            NegotiationConversationSnapshot snapshot
    ) {
        if (snapshot.result()
                == NegotiationConversationResult.SELLER_COUNTER_OFFER) {
            log.info(
                    "[CONVERSATION] Conversation {} for listing {} was classified as SELLER_COUNTER_OFFER. Seller price: {}",
                    listing.conversationId(),
                    listing.listingId(),
                    snapshot.sellerCounterOfferPrice()
            );
            return;
        }

        log.info(
                "[CONVERSATION] Conversation {} for listing {} was classified as {}. Raw status: {}",
                listing.conversationId(),
                listing.listingId(),
                snapshot.result(),
                snapshot.rawStatus()
        );
    }

    private void verifyOpenedConversationReadOnly(
            Page page,
            ListingResponseDto listing
    ) {
        ConversationIdentityResolver.ConversationIdentityAssessment assessment =
                new ConversationIdentityResolver().assess(
                        listing.conversationId(),
                        page.url()
                );

        if (!assessment.matchesExpectedConversation()) {
            throw new IllegalStateException(
                    "Recovery inspection belongs to an unexpected conversation. Expected: "
                            + listing.conversationId()
                            + ", actual: "
                            + assessment.actualConversationId()
                            + ", URL: "
                            + page.url()
            );
        }

        if (assessment.canonicalRedirect()) {
            log.info(
                    "[NEGOTIATION RECOVERY] Vinted canonicalized terminal conversation {} to {} for listing {}. Read-only inspection will continue without persisting the route.",
                    listing.conversationId(),
                    assessment.actualConversationId(),
                    listing.listingId()
            );
        }
    }

    private ListingResponseDto validateOpenedConversation(
            Page page,
            ListingResponseDto listing
    ) {
        ListingResponseDto canonicalListing =
                new ConversationIdentityCoordinator(context)
                        .verifyAndCanonicalize(
                                listing,
                                "Opened conversation"
                        );

        log.info(
                "[CONVERSATION] Opened expected conversation {}",
                canonicalListing.conversationId()
        );

        return canonicalListing;
    }

    private void validateRecoveryListing(
            ListingResponseDto listing
    ) {
        if (listing.id() == null) {
            throw new IllegalArgumentException(
                    "Backend listing ID cannot be null"
            );
        }

        if (!"REJECTED".equals(listing.status())
                && !"EXPIRED".equals(listing.status())
                && !"UNAVAILABLE".equals(listing.status())
                && !"CONTACT_UNAVAILABLE".equals(listing.status())) {
            throw new IllegalArgumentException(
                    "Recovery inspection only supports recent technical terminal listings. Backend listing: "
                            + listing.id()
                            + ", current status: "
                            + listing.status()
            );
        }

        if (listing.currentStep() == null
                || listing.currentStep() <= 0) {
            throw new IllegalArgumentException(
                    "Terminal negotiation "
                            + listing.id()
                            + " has an invalid current step: "
                            + listing.currentStep()
            );
        }

        if (listing.conversationId() == null
                || listing.conversationId().isBlank()) {
            throw new IllegalArgumentException(
                    "Terminal negotiation "
                            + listing.id()
                            + " has no conversation ID"
            );
        }

        if (listing.conversationUrl() == null
                || listing.conversationUrl().isBlank()) {
            throw new IllegalArgumentException(
                    "Terminal negotiation "
                            + listing.id()
                            + " has no conversation URL"
            );
        }
    }

    private void validateListing(
            ListingResponseDto listing
    ) {
        if (listing.id() == null) {
            throw new IllegalArgumentException(
                    "Backend listing ID cannot be null"
            );
        }

        if (!"NEGOTIATING".equals(listing.status())) {
            throw new IllegalArgumentException(
                    "Conversation can only be inspected for a NEGOTIATING listing. Backend listing: "
                            + listing.id()
                            + ", current status: "
                            + listing.status()
            );
        }

        if (listing.conversationId() == null
                || listing.conversationId().isBlank()) {
            throw new IllegalArgumentException(
                    "Negotiating listing "
                            + listing.id()
                            + " has no conversation ID"
            );
        }

        if (listing.conversationUrl() == null
                || listing.conversationUrl().isBlank()) {
            throw new IllegalArgumentException(
                    "Negotiating listing "
                            + listing.id()
                            + " has no conversation URL"
            );
        }
    }
}

package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

/**
 * Exact trusted-conversation navigation and canonicalization before a later
 * real offer. It never sends offers or mutates persistent backend state.
 */
@Slf4j
final class NextStepConversationReadiness {
    private static final double CONVERSATION_TIMEOUT_MS = 20_000;
    private final BotContext context;
    private final HumanVerificationHandler humanVerificationHandler;

    NextStepConversationReadiness(BotContext context, HumanVerificationHandler handler) {
        this.context = context;
        this.humanVerificationHandler = handler;
    }

    ListingResponseDto openConversation(
            Page page,
            ListingResponseDto listing,
            String logPrefix
    ) {

        boolean reuseCurrentConversation =
                isExpectedConversationAlreadyOpen(
                        page,
                        listing
                );

        if (reuseCurrentConversation) {
            log.info(
                    "{} Reusing already-open expected conversation {} instead of navigating to the same inbox URL again.",
                    logPrefix,
                    listing.conversationId()
            );
        } else {
            log.info(
                    "{} Opening conversation {}: {}",
                    logPrefix,
                    listing.conversationId(),
                    listing.conversationUrl()
            );

            new MarketplaceNavigator(context).goToTrustedVintedUrl(
                    listing.conversationUrl()
            );
        }

        humanVerificationHandler.waitUntilVerified(
                page
        );

        ListingResponseDto canonicalListing =
                validateOpenedConversation(
                        page,
                        listing
                );

        Locator conversationContent =
                page.getByTestId(
                                "conversation-content"
                        )
                        .first();

        conversationContent.waitFor(
                new Locator.WaitForOptions()
                        .setState(
                                WaitForSelectorState.VISIBLE
                        )
                        .setTimeout(
                                CONVERSATION_TIMEOUT_MS
                        )
        );

        log.info(
                "{} Conversation {} is ready.",
                logPrefix,
                canonicalListing.conversationId()
        );

        return canonicalListing;
    }

    boolean isExpectedConversationAlreadyOpen(
            Page page,
            ListingResponseDto listing
    ) {
        if (page == null
                || page.isClosed()
                || listing == null
                || listing.conversationId() == null
                || listing.conversationId().isBlank()) {
            return false;
        }

        try {
            ConversationIdentityResolver.ConversationIdentityAssessment assessment =
                    new ConversationIdentityResolver().assess(
                            listing.conversationId(),
                            page.url()
                    );

            if (!assessment.matchesExpectedConversation()) {
                return false;
            }

            Locator conversationContent =
                    page.getByTestId("conversation-content").first();

            return conversationContent.isVisible();
        } catch (RuntimeException exception) {
            return false;
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
                "Opened expected conversation {}.",
                canonicalListing.conversationId()
        );

        return canonicalListing;
    }
}

package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.api.listing.dto.UpdateConversationIdentityRequestDto;
import pl.flipbot.playwright.context.BotContext;

import java.util.Objects;

/**
 * Applies the pure conversation-identity assessment to the live page and,
 * only for a proven Vinted canonical redirect, persists the canonical route in
 * the backend through a narrow endpoint that does not touch negotiation state.
 */
@Slf4j
@RequiredArgsConstructor
final class ConversationIdentityCoordinator {

    private final BotContext context;
    private final ListingClient listingClient = new ListingClient();
    private final ConversationIdentityResolver resolver =
            new ConversationIdentityResolver();

    ListingResponseDto verifyAndCanonicalize(
            ListingResponseDto listing,
            String operation
    ) {
        Objects.requireNonNull(listing, "Listing cannot be null");

        Page page = context.getPage();
        String currentUrl = page.url();

        ConversationIdentityResolver.ConversationIdentityAssessment assessment =
                resolver.assess(
                        listing.conversationId(),
                        currentUrl
                );

        if (!assessment.matchesExpectedConversation()) {
            throw new IllegalStateException(
                    operation
                            + " belongs to an unexpected conversation. Expected: "
                            + listing.conversationId()
                            + ", actual: "
                            + assessment.actualConversationId()
                            + ", URL: "
                            + currentUrl
                            + ", referrer: "
                            + assessment.referrer()
            );
        }

        if (!assessment.canonicalRedirect()) {
            return listing;
        }

        log.warn(
                "[CONVERSATION CANONICALIZE] Vinted redirected backend listing {} / marketplace listing {} from stored conversation {} to canonical conversation {}. Evidence: final URL referrer points to the exact stored conversation. Persisting only conversation identity before continuing.",
                listing.id(),
                listing.listingId(),
                listing.conversationId(),
                assessment.actualConversationId()
        );

        ListingResponseDto updated =
                listingClient.updateConversationIdentity(
                        context.getBot().getId(),
                        listing.id(),
                        new UpdateConversationIdentityRequestDto(
                                assessment.actualConversationId(),
                                assessment.canonicalConversationUrl()
                        )
                );

        if (!Objects.equals(listing.id(), updated.id())
                || !"NEGOTIATING".equals(updated.status())
                || !Objects.equals(
                assessment.actualConversationId(),
                updated.conversationId()
        )
                || !Objects.equals(
                assessment.canonicalConversationUrl(),
                updated.conversationUrl()
        )) {
            throw new IllegalStateException(
                    "Backend returned unexpected state while canonicalizing conversation for listing "
                            + listing.listingId()
            );
        }

        log.warn(
                "[CONVERSATION CANONICALIZE] Canonical conversation persisted successfully for marketplace listing {}. oldId={}, newId={}, canonicalUrl={}",
                listing.listingId(),
                listing.conversationId(),
                updated.conversationId(),
                updated.conversationUrl()
        );

        return updated;
    }
}

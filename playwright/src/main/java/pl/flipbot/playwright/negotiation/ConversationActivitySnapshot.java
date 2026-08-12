package pl.flipbot.playwright.negotiation;

import java.time.LocalDateTime;

public record ConversationActivitySnapshot(
        boolean inspectionSucceeded,
        boolean latestOwnOfferFound,
        boolean sellerMessageAfterLatestOwnOffer,
        String latestSellerMessageText,
        LocalDateTime latestSellerMessageAt,
        boolean readIndicatorAfterLatestOwnOffer
) {

    public static ConversationActivitySnapshot unavailable() {

        return new ConversationActivitySnapshot(
                false,
                false,
                false,
                null,
                null,
                false
        );
    }
}
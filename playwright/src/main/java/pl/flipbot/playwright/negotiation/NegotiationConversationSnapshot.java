package pl.flipbot.playwright.negotiation;

import java.math.BigDecimal;

public record NegotiationConversationSnapshot(

        NegotiationConversationResult result,

        BigDecimal sellerCounterOfferPrice,

        String rawStatus

) {

    public static NegotiationConversationSnapshot pending(
            String rawStatus
    ) {

        return new NegotiationConversationSnapshot(
                NegotiationConversationResult.PENDING,
                null,
                rawStatus
        );

    }

    public static NegotiationConversationSnapshot accepted(
            String rawStatus
    ) {

        return new NegotiationConversationSnapshot(
                NegotiationConversationResult.ACCEPTED,
                null,
                rawStatus
        );

    }

    public static NegotiationConversationSnapshot rejected(
            String rawStatus
    ) {

        return new NegotiationConversationSnapshot(
                NegotiationConversationResult.REJECTED,
                null,
                rawStatus
        );

    }

    public static NegotiationConversationSnapshot sellerCounterOffer(
            BigDecimal price
    ) {

        return new NegotiationConversationSnapshot(
                NegotiationConversationResult.SELLER_COUNTER_OFFER,
                price,
                null
        );

    }

    public static NegotiationConversationSnapshot unknown() {

        return new NegotiationConversationSnapshot(
                NegotiationConversationResult.UNKNOWN,
                null,
                null
        );

    }

}
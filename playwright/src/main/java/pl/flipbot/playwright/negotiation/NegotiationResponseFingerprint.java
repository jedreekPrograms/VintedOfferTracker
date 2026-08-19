package pl.flipbot.playwright.negotiation;

import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;

import java.math.BigDecimal;

final class NegotiationResponseFingerprint {

    private NegotiationResponseFingerprint() {
    }

    static String create(
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
}

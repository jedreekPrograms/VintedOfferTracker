package pl.flipbot.listing.dto;

import java.time.LocalDateTime;

public record NegotiationActivityRequest(
        LocalDateTime sellerActivityAt,
        boolean readDetected,
        String formalResponseFingerprint
) {
    public NegotiationActivityRequest(
            LocalDateTime sellerActivityAt,
            boolean readDetected
    ) {
        this(sellerActivityAt, readDetected, null);
    }
}

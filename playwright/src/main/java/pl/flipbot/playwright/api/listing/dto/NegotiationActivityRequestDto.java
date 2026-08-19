package pl.flipbot.playwright.api.listing.dto;

import java.time.LocalDateTime;

public record NegotiationActivityRequestDto(
        LocalDateTime sellerActivityAt,
        boolean readDetected,
        String formalResponseFingerprint
) {
    public NegotiationActivityRequestDto(
            LocalDateTime sellerActivityAt,
            boolean readDetected
    ) {
        this(sellerActivityAt, readDetected, null);
    }
}

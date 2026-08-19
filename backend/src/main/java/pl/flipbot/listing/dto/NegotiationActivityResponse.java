package pl.flipbot.listing.dto;

import java.time.LocalDateTime;

public record NegotiationActivityResponse(
        Long backendListingId,
        Integer currentStep,
        LocalDateTime currentStepStartedAt,
        LocalDateTime sellerActivityAt,
        LocalDateTime readDetectedAt,
        String formalResponseFingerprint,
        LocalDateTime formalResponseDetectedAt
) {
}

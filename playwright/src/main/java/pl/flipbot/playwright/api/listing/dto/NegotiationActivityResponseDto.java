package pl.flipbot.playwright.api.listing.dto;

public record NegotiationActivityResponseDto(
        Long backendListingId,
        Integer currentStep,
        String currentStepStartedAt,
        String sellerActivityAt,
        String readDetectedAt
) {
}
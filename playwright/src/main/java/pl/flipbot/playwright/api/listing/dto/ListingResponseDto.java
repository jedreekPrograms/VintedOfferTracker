package pl.flipbot.playwright.api.listing.dto;

import java.math.BigDecimal;

public record ListingResponseDto(
        Long id,
        String listingId,
        String title,
        String url,
        BigDecimal originalPrice,
        BigDecimal currentPrice,
        Integer currentStep,
        Boolean awaitingSellerResponse,
        String conversationId,
        String conversationUrl,
        String status,
        String decisionAt,
        String currentStepStartedAt,
        String sellerActivityAt,
        String readDetectedAt,
        String formalResponseFingerprint,
        String formalResponseDetectedAt,
        String negotiationStrategySnapshot
) {

    /* Backward-compatible constructor used by older tests/helpers. */
    public ListingResponseDto(
            Long id,
            String listingId,
            String title,
            String url,
            BigDecimal originalPrice,
            BigDecimal currentPrice,
            Integer currentStep,
            Boolean awaitingSellerResponse,
            String conversationId,
            String conversationUrl,
            String status,
            String decisionAt
    ) {
        this(
                id,
                listingId,
                title,
                url,
                originalPrice,
                currentPrice,
                currentStep,
                awaitingSellerResponse,
                conversationId,
                conversationUrl,
                status,
                decisionAt,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    /* Compatibility with code written after negotiation activity timers but
       before formal-response timers were introduced. */
    public ListingResponseDto(
            Long id,
            String listingId,
            String title,
            String url,
            BigDecimal originalPrice,
            BigDecimal currentPrice,
            Integer currentStep,
            Boolean awaitingSellerResponse,
            String conversationId,
            String conversationUrl,
            String status,
            String decisionAt,
            String currentStepStartedAt,
            String sellerActivityAt,
            String readDetectedAt
    ) {
        this(
                id,
                listingId,
                title,
                url,
                originalPrice,
                currentPrice,
                currentStep,
                awaitingSellerResponse,
                conversationId,
                conversationUrl,
                status,
                decisionAt,
                currentStepStartedAt,
                sellerActivityAt,
                readDetectedAt,
                null,
                null,
                null
        );
    }

    public ListingResponseDto(
            Long id,
            String listingId,
            String title,
            String url,
            BigDecimal originalPrice,
            BigDecimal currentPrice,
            Integer currentStep,
            Boolean awaitingSellerResponse,
            String conversationId,
            String conversationUrl,
            String status,
            String decisionAt,
            String currentStepStartedAt,
            String sellerActivityAt,
            String readDetectedAt,
            String formalResponseFingerprint,
            String formalResponseDetectedAt
    ) {
        this(
                id,
                listingId,
                title,
                url,
                originalPrice,
                currentPrice,
                currentStep,
                awaitingSellerResponse,
                conversationId,
                conversationUrl,
                status,
                decisionAt,
                currentStepStartedAt,
                sellerActivityAt,
                readDetectedAt,
                formalResponseFingerprint,
                formalResponseDetectedAt,
                null
        );
    }
}

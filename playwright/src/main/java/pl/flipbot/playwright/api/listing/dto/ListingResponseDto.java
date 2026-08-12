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
        String readDetectedAt
) {

    /*
     * Zachowujemy konstruktor ze starego kontraktu.
     * Dzięki temu ewentualne testy / pomocniczy kod, który ręcznie
     * tworzy ListingResponseDto z 12 polami, nadal się kompiluje.
     */
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
                null
        );
    }
}
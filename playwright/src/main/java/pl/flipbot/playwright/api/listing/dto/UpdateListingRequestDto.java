package pl.flipbot.playwright.api.listing.dto;

import java.math.BigDecimal;

public record UpdateListingRequestDto(

        String status,

        BigDecimal currentPrice,

        Integer currentStep,

        Boolean awaitingSellerResponse,

        String conversationId,

        String conversationUrl

) {
}
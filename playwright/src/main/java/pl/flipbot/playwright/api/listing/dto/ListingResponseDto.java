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

        String status

) {
}
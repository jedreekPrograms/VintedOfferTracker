package pl.flipbot.playwright.api.listing.dto;

import java.math.BigDecimal;

public record CreateListingRequestDto(

        String listingId,

        String title,

        String url,

        BigDecimal originalPrice

) {
}
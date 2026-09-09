package pl.flipbot.playwright.marketstats.dto;

import java.util.Map;

public record MarketListingPublicationBatchRequestDto(
        Map<String, String> publishedAtByListingId
) {
}

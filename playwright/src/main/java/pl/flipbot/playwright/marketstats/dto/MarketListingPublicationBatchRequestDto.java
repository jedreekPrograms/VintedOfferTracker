package pl.flipbot.playwright.marketstats.dto;

import java.util.Map;

public record MarketListingPublicationBatchRequestDto(
        Integer trackingGeneration,
        Map<String, String> publishedAtByListingId
) {
}

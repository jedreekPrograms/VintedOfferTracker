package pl.flipbot.playwright.marketstats.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record MarketObservationBatchRequestDto(
        List<String> listingIds,
        Map<String, String> publishedAtByListingId,
        boolean complete,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}

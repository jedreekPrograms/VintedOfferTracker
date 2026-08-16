package pl.flipbot.playwright.marketstats.dto;

import java.math.BigDecimal;
import java.util.List;

public record MarketObservationBatchRequestDto(
        List<String> listingIds,
        boolean complete,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}

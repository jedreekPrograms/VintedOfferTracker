package pl.flipbot.playwright.marketstats.dto;

import java.util.List;

public record MarketObservationBatchRequestDto(
        List<String> listingIds,
        boolean complete
) {
}

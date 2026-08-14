package pl.flipbot.playwright.marketstats.dto;

import java.util.List;

public record KnownMarketListingIdsDto(
        Long modelId,
        List<String> listingIds,
        boolean baselineComplete
) {
}

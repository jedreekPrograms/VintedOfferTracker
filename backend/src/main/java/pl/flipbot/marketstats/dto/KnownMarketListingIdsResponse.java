package pl.flipbot.marketstats.dto;

import java.util.List;

public record KnownMarketListingIdsResponse(
        Long modelId,
        List<String> listingIds
) {
}

package pl.flipbot.marketstats.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record MarketListingPublicationBatchRequest(
        Integer trackingGeneration,
        @NotNull Map<String, String> publishedAtByListingId
) {
}

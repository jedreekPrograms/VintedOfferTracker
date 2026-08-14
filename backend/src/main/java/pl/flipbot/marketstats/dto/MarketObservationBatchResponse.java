package pl.flipbot.marketstats.dto;

import java.time.LocalDateTime;

public record MarketObservationBatchResponse(
        Long modelId,
        boolean baselineCreated,
        int observedListings,
        int newListings,
        LocalDateTime scannedAt,
        boolean complete
) {
}

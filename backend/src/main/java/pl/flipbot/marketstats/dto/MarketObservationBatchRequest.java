package pl.flipbot.marketstats.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MarketObservationBatchRequest(
        @NotNull List<String> listingIds,
        boolean complete
) {
}

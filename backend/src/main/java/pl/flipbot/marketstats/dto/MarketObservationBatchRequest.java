package pl.flipbot.marketstats.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MarketObservationBatchRequest(
        @NotEmpty List<String> listingIds,
        boolean complete
) {
}

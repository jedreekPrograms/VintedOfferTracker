package pl.flipbot.marketstats.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public record MarketObservationBatchRequest(
        @NotNull List<String> listingIds,
        boolean complete,
        BigDecimal minPrice,
        BigDecimal maxPrice
) {
}

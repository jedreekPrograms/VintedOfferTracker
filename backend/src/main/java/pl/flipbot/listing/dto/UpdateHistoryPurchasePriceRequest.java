package pl.flipbot.listing.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateHistoryPurchasePriceRequest(
        @NotNull
        @DecimalMin(value = "0.01", message = "Purchase price must be greater than zero")
        BigDecimal purchasePrice
) {
}

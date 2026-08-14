package pl.flipbot.dictionary.dto;

import jakarta.validation.constraints.DecimalMin;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateDictionaryModelPricingRequest {

    @DecimalMin(
            value = "0.01",
            message = "Proposed offer price must be greater than 0"
    )
    private BigDecimal proposedOfferPrice;

    @DecimalMin(
            value = "0.01",
            message = "Expected resale price must be greater than 0"
    )
    private BigDecimal expectedResalePrice;
}

package pl.flipbot.listing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.listing.ListingStatus;

import java.math.BigDecimal;

@Getter
@Setter
public class UpdateListingRequest {

    @NotNull
    private ListingStatus status;

    @NotNull
    private BigDecimal currentPrice;

    @NotNull
    private Integer currentStep;
}

package pl.flipbot.listing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateListingRequest {

    @NotBlank
    private String listingId;

    @NotBlank
    private String title;

    @NotBlank
    private String url;

    @NotNull
    private BigDecimal originalPrice;
}

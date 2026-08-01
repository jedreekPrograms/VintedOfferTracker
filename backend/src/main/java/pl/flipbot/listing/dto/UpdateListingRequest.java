package pl.flipbot.listing.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @NotNull
    private Boolean awaitingSellerResponse;

    @Size(max = 255)
    private String conversationId;

    @Size(max = 1000)
    private String conversationUrl;

}
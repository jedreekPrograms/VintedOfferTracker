package pl.flipbot.negotiation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.negotiation.NegotiationResult;

import java.math.BigDecimal;

@Getter
@Setter
public class NegotiationResultRequest {

    @NotBlank
    private String listingId;

    @NotNull
    private NegotiationResult result;

    private BigDecimal counterOffer;
}

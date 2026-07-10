package pl.flipbot.negotiation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class CreateNegotiationStepRequest {

    @NotNull
    private BigDecimal offerPrice;

    @NotNull
    private BigDecimal maxAcceptedCounterOffer;

    @NotBlank
    private String message;
}

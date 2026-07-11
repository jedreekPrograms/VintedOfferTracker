package pl.flipbot.negotiation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.negotiation.NegotiationResult;

import java.math.BigDecimal;

@Getter
@Setter
public class NegotiationResultRequest {

    @NotNull
    private NegotiationResult result;

    private BigDecimal counterOffer;
}

package pl.flipbot.negotiation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.negotiation.NegotiationReactionAction;

import java.math.BigDecimal;

@Getter
@Setter
public class SellerCounterOfferRuleRequest {

    @NotNull
    @DecimalMin(value = "0.001")
    @DecimalMax(value = "100.000")
    private BigDecimal minimumDiscountPercent;

    @NotNull
    private NegotiationReactionAction action;

    private Integer waitHours;
}

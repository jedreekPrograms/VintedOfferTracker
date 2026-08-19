package pl.flipbot.negotiation.dto;

import lombok.Builder;
import lombok.Getter;
import pl.flipbot.negotiation.NegotiationReactionAction;

import java.math.BigDecimal;

@Getter
@Builder
public class SellerCounterOfferRuleResponse {
    private BigDecimal minimumDiscountPercent;
    private NegotiationReactionAction action;
    private Integer waitHours;
}

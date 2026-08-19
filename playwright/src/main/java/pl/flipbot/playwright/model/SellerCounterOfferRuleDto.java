package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class SellerCounterOfferRuleDto {
    private BigDecimal minimumDiscountPercent;
    private NegotiationReactionAction action;
    private Integer waitHours;
}

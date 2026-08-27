package pl.flipbot.negotiation;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class NegotiationDecision {

    private NegotiationAction action;

    private BigDecimal offerPrice;

    private String message;

    private Integer nextStep;
}

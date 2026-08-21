package pl.flipbot.negotiation.dto;

import lombok.Builder;
import lombok.Getter;
import pl.flipbot.negotiation.NegotiationReactionAction;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Builder
public class NegotiationStepResponse {

    private Integer stepNumber;

    private BigDecimal offerPrice;

    private BigDecimal maxAcceptedCounterOffer;

    private String message;

    private NegotiationReactionAction rejectionAction;

    private Integer rejectionWaitHours;

    private Integer readWaitHours;

    private Integer unreadWaitHours;

    private NegotiationReactionAction counterOfferDefaultAction;

    private Integer counterOfferDefaultWaitHours;

    private List<SellerCounterOfferRuleResponse> counterOfferRules;
}

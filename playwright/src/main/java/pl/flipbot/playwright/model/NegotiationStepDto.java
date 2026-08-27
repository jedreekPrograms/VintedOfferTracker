package pl.flipbot.playwright.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class NegotiationStepDto {

    private Integer stepNumber;

    private BigDecimal offerPrice;

    private BigDecimal maxAcceptedCounterOffer;

    private String message;

    private NegotiationReactionAction rejectionAction;

    private Integer rejectionWaitHours;

    private NegotiationReactionAction counterOfferDefaultAction;

    private Integer counterOfferDefaultWaitHours;

    private List<SellerCounterOfferRuleDto> counterOfferRules =
            new ArrayList<>();
}

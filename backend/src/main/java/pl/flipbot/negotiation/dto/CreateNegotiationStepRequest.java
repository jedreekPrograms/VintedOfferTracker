package pl.flipbot.negotiation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.negotiation.NegotiationReactionAction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class CreateNegotiationStepRequest {

    @NotNull
    private BigDecimal offerPrice;

    @NotNull
    private BigDecimal maxAcceptedCounterOffer;

    @NotBlank
    private String message;

    @NotNull
    private NegotiationReactionAction rejectionAction;

    private Integer rejectionWaitHours;

    @NotNull
    private NegotiationReactionAction counterOfferDefaultAction;

    private Integer counterOfferDefaultWaitHours;

    @Valid
    @NotNull
    private List<SellerCounterOfferRuleRequest> counterOfferRules =
            new ArrayList<>();
}

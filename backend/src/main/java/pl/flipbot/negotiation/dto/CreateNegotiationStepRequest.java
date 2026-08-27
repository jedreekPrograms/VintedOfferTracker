package pl.flipbot.negotiation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.negotiation.NegotiationReactionAction;

import java.math.BigDecimal;
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

    /*
     * Nullable on the transport boundary for backward compatibility with an
     * older frontend. BotService resolves missing values to the new sensible
     * defaults before persisting them.
     */
    private NegotiationReactionAction rejectionAction;

    private Integer rejectionWaitHours;

    private NegotiationReactionAction counterOfferDefaultAction;

    private Integer counterOfferDefaultWaitHours;

    /*
     * null = old client omitted the field -> apply default 10%/15% rules.
     * []   = new client intentionally wants no discount thresholds.
     */
    @Valid
    private List<SellerCounterOfferRuleRequest> counterOfferRules;
}

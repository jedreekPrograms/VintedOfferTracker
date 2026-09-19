package pl.flipbot.negotiation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import pl.flipbot.negotiation.NegotiationReactionAction;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class CreateNegotiationStepRequest {

    @NotNull
    @Positive
    private BigDecimal offerPrice;

    @NotNull
    @Positive
    private BigDecimal maxAcceptedCounterOffer;

    @NotBlank
    private String message;

    @AssertTrue(
            message = "Maximum accepted counteroffer cannot be lower than our offer price."
    )
    public boolean isAcceptedCounterOfferCompatibleWithOwnOffer() {
        if (offerPrice == null || maxAcceptedCounterOffer == null) {
            return true;
        }

        return maxAcceptedCounterOffer.compareTo(offerPrice) >= 0;
    }

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

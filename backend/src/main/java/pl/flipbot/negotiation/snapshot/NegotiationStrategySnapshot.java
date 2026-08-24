package pl.flipbot.negotiation.snapshot;

import pl.flipbot.negotiation.NegotiationReactionAction;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable JSON payload persisted on a listing when its negotiation starts.
 * It deliberately contains only negotiation semantics, not Vinted account or
 * target identity. Those remain live bot properties and stay protected by the
 * active-negotiation edit guard.
 */
public record NegotiationStrategySnapshot(
        int schemaVersion,
        NegotiationPricingMode pricingMode,
        Boolean autoRaiseOfferToVintedMinimum,
        BigDecimal maxAutomaticOffer,
        List<Step> steps
) {

    public record Step(
            Integer stepNumber,
            BigDecimal offerPrice,
            BigDecimal maxAcceptedCounterOffer,
            String message,
            NegotiationReactionAction rejectionAction,
            Integer rejectionWaitHours,
            NegotiationReactionAction counterOfferDefaultAction,
            Integer counterOfferDefaultWaitHours,
            List<CounterOfferRule> counterOfferRules
    ) {
    }

    public record CounterOfferRule(
            BigDecimal minimumDiscountPercent,
            NegotiationReactionAction action,
            Integer waitHours
    ) {
    }
}

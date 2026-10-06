package pl.flipbot.negotiation.strategy;

import pl.flipbot.negotiation.NegotiationReactionAction;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable negotiation strategy captured when a real conversation starts.
 *
 * The live product configuration may be edited later. Existing negotiations
 * must continue with the ladder, messages and response policies that were
 * active when their first offer was sent.
 */
public record NegotiationStrategySnapshot(
        Integer version,
        Boolean autoRaiseOfferToVintedMinimum,
        BigDecimal maxAutomaticOffer,
        List<NegotiationStepSnapshot> negotiationSteps
) {

    public record NegotiationStepSnapshot(
            Integer stepNumber,
            BigDecimal offerPrice,
            BigDecimal maxAcceptedCounterOffer,
            String message,
            NegotiationReactionAction rejectionAction,
            Integer rejectionWaitHours,
            Integer readWaitHours,
            Integer unreadWaitHours,
            NegotiationReactionAction counterOfferDefaultAction,
            Integer counterOfferDefaultWaitHours,
            List<SellerCounterOfferRuleSnapshot> counterOfferRules
    ) {
    }

    public record SellerCounterOfferRuleSnapshot(
            BigDecimal minimumDiscountPercent,
            NegotiationReactionAction action,
            Integer waitHours
    ) {
    }
}

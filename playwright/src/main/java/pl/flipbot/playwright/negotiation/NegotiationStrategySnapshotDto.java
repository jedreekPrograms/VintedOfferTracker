package pl.flipbot.playwright.negotiation;

import pl.flipbot.playwright.model.NegotiationReactionAction;

import java.math.BigDecimal;
import java.util.List;

record NegotiationStrategySnapshotDto(
        Integer schemaVersion,
        String pricingMode,
        Boolean autoRaiseOfferToVintedMinimum,
        BigDecimal maxAutomaticOffer,
        List<Step> steps
) {
    record Step(
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

    record CounterOfferRule(
            BigDecimal minimumDiscountPercent,
            NegotiationReactionAction action,
            Integer waitHours
    ) {
    }
}

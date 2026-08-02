package pl.flipbot.playwright.negotiation;

import pl.flipbot.playwright.model.NegotiationStepDto;

import java.math.BigDecimal;

public record NegotiationDecision(

        NegotiationDecisionType type,

        NegotiationStepDto nextStep,

        BigDecimal sellerCounterOfferPrice,

        String reason

) {

    public static NegotiationDecision waitForSeller() {

        return new NegotiationDecision(
                NegotiationDecisionType.WAIT,
                null,
                null,
                "The latest offer is still pending"
        );

    }

    public static NegotiationDecision actionRequiredAfterAcceptance() {

        return new NegotiationDecision(
                NegotiationDecisionType.MARK_ACTION_REQUIRED,
                null,
                null,
                "The seller accepted the latest offer"
        );

    }

    public static NegotiationDecision actionRequiredForCounterOffer(
            BigDecimal sellerCounterOfferPrice
    ) {

        return new NegotiationDecision(
                NegotiationDecisionType.MARK_ACTION_REQUIRED,
                null,
                sellerCounterOfferPrice,
                "The seller counteroffer is within the accepted limit"
        );

    }

    public static NegotiationDecision sendNextStep(
            NegotiationStepDto nextStep,
            BigDecimal sellerCounterOfferPrice,
            String reason
    ) {

        return new NegotiationDecision(
                NegotiationDecisionType.SEND_NEXT_STEP,
                nextStep,
                sellerCounterOfferPrice,
                reason
        );

    }

    public static NegotiationDecision rejected(
            BigDecimal sellerCounterOfferPrice,
            String reason
    ) {

        return new NegotiationDecision(
                NegotiationDecisionType.MARK_REJECTED,
                null,
                sellerCounterOfferPrice,
                reason
        );

    }

    public static NegotiationDecision unknown() {

        return new NegotiationDecision(
                NegotiationDecisionType.KEEP_UNKNOWN,
                null,
                null,
                "The latest conversation event could not be recognized"
        );

    }

}
package pl.flipbot.playwright.negotiation;

import pl.flipbot.playwright.model.NegotiationStepDto;

public record PendingNegotiationDecision(
        Action action,
        NegotiationStepDto nextStep,
        String reason
) {

    public enum Action {
        WAIT,
        SEND_NEXT_STEP,
        EXPIRE
    }


    public static PendingNegotiationDecision waitForSeller(
            String reason
    ) {

        return new PendingNegotiationDecision(
                Action.WAIT,
                null,
                reason
        );
    }


    public static PendingNegotiationDecision sendNextStep(
            NegotiationStepDto nextStep,
            String reason
    ) {

        return new PendingNegotiationDecision(
                Action.SEND_NEXT_STEP,
                nextStep,
                reason
        );
    }


    public static PendingNegotiationDecision expire(
            String reason
    ) {

        return new PendingNegotiationDecision(
                Action.EXPIRE,
                null,
                reason
        );
    }
}
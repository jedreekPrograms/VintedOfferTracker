package pl.flipbot.playwright.negotiation;

public record TerminalNegotiationRecoveryDecision(
        Action action,
        NegotiationDecision negotiationDecision,
        boolean awaitingSellerResponse,
        String reason
) {

    public enum Action {
        KEEP_TERMINAL,
        REOPEN_NEGOTIATING,
        MARK_ACTION_REQUIRED
    }

    public static TerminalNegotiationRecoveryDecision keep(
            String reason
    ) {
        return new TerminalNegotiationRecoveryDecision(
                Action.KEEP_TERMINAL,
                null,
                false,
                reason
        );
    }

    public static TerminalNegotiationRecoveryDecision reopen(
            boolean awaitingSellerResponse,
            String reason
    ) {
        return new TerminalNegotiationRecoveryDecision(
                Action.REOPEN_NEGOTIATING,
                null,
                awaitingSellerResponse,
                reason
        );
    }

    public static TerminalNegotiationRecoveryDecision actionRequired(
            NegotiationDecision decision
    ) {
        return new TerminalNegotiationRecoveryDecision(
                Action.MARK_ACTION_REQUIRED,
                decision,
                false,
                decision.reason()
        );
    }
}

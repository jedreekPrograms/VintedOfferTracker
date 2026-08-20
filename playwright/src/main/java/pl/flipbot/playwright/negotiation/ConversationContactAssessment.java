package pl.flipbot.playwright.negotiation;

public record ConversationContactAssessment(
        State state,
        String reason
) {

    public enum State {
        AVAILABLE,
        SUSPECTED_UNAVAILABLE,
        CONFIRMED_UNAVAILABLE
    }

    public static ConversationContactAssessment available(String reason) {
        return new ConversationContactAssessment(
                State.AVAILABLE,
                reason
        );
    }

    public static ConversationContactAssessment suspected(String reason) {
        return new ConversationContactAssessment(
                State.SUSPECTED_UNAVAILABLE,
                reason
        );
    }

    public static ConversationContactAssessment confirmed(String reason) {
        return new ConversationContactAssessment(
                State.CONFIRMED_UNAVAILABLE,
                reason
        );
    }
}

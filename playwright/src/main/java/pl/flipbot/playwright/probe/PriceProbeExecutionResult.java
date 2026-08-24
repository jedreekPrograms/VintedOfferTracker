package pl.flipbot.playwright.probe;

public record PriceProbeExecutionResult(
        State state,
        String details
) {
    public enum State {
        SENT,
        FAILED,
        UNKNOWN
    }

    public static PriceProbeExecutionResult sent() {
        return new PriceProbeExecutionResult(State.SENT, null);
    }

    public static PriceProbeExecutionResult failed(String details) {
        return new PriceProbeExecutionResult(State.FAILED, details);
    }

    public static PriceProbeExecutionResult unknown(String details) {
        return new PriceProbeExecutionResult(State.UNKNOWN, details);
    }
}

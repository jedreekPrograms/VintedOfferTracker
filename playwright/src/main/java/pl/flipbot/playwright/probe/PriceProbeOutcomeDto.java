package pl.flipbot.playwright.probe;

public record PriceProbeOutcomeDto(
        String outcome,
        String details
) {
    public static PriceProbeOutcomeDto sent() {
        return new PriceProbeOutcomeDto("SENT", null);
    }

    public static PriceProbeOutcomeDto failed(String details) {
        return new PriceProbeOutcomeDto("FAILED", details);
    }

    public static PriceProbeOutcomeDto unknown(String details) {
        return new PriceProbeOutcomeDto("UNKNOWN", details);
    }
}

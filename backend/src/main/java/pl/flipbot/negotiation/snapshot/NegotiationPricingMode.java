package pl.flipbot.negotiation.snapshot;

/**
 * Pricing semantics frozen into a negotiation when the conversation starts.
 *
 * LEGACY_RATIO preserves negotiations that were already active before the
 * snapshot feature was introduced. DECREASING_CONCESSIONS is the research-
 * based strategy used for newly started negotiations.
 */
public enum NegotiationPricingMode {
    LEGACY_RATIO,
    DECREASING_CONCESSIONS
}

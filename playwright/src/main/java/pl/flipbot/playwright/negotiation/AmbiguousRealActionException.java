package pl.flipbot.playwright.negotiation;

/**
 * Signals that a real marketplace action may already have been submitted,
 * but the worker could not confirm the final result reliably.
 *
 * This is deliberately distinct from ordinary per-listing inspection errors:
 * callers must abort the current real-action job instead of continuing with
 * another listing, because automatic follow-up work after an ambiguous submit
 * would weaken the fail-closed guarantee.
 */
public class AmbiguousRealActionException extends RealActionJobAbortException {

    public AmbiguousRealActionException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}

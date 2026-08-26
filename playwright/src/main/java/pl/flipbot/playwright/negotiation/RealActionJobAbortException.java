package pl.flipbot.playwright.negotiation;

/**
 * Marker for failures that happen only after a real marketplace action may
 * already have been delivered (or has been confirmed as delivered).
 *
 * Ordinary per-listing inspection/preparation failures may be isolated and the
 * worker can continue with another listing. This exception is different: the
 * current real-action job must stop so a post-submit failure can never cause a
 * second real action merely because the normal per-run counter was not reached.
 */
public class RealActionJobAbortException extends RuntimeException {

    public RealActionJobAbortException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}

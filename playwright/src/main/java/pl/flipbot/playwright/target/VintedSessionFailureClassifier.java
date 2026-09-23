package pl.flipbot.playwright.target;

/**
 * Compatibility hook for historical authentication-stall classification.
 *
 * <p>SESSION_BLOCKED is a strong statement shown to the operator and drives the
 * persisted exponential session cooldown. It must therefore be produced only
 * when {@link VintedSessionBlockDetector} has actual block-page evidence.
 * A stalled login form is not enough: it can also mean an expired/missing
 * storageState, a changed authentication UI, a transient network problem or an
 * ordinary login failure.</p>
 *
 * <p>The method remains temporarily so older call sites compile while the
 * scheduler keeps the late explicit block-page polling path. Generic login
 * stalls now stay on the normal RUN_FAILED path instead of being mislabeled as
 * a Vinted session block.</p>
 */
public final class VintedSessionFailureClassifier {

    private VintedSessionFailureClassifier() {
    }

    public static boolean shouldUseProtectiveCooldown(Throwable throwable) {
        return false;
    }
}

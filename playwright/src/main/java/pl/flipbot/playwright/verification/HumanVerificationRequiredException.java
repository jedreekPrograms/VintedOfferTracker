package pl.flipbot.playwright.verification;

import pl.flipbot.playwright.target.VintedRateLimitException;

/**
 * Positive CAPTCHA evidence remained after the manual-completion window.
 * Extends the existing traffic-stop signal so nested offer/catalog processors
 * propagate it instead of continuing through more listings. The scheduler
 * handles it separately from an explicit rate limit or blocked session.
 */
public final class HumanVerificationRequiredException extends VintedRateLimitException {

    public static final long RETRY_DELAY_MILLIS = 15L * 60_000L;

    public HumanVerificationRequiredException(String message) {
        super(message);
    }
}

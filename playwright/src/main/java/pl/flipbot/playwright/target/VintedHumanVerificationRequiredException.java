package pl.flipbot.playwright.target;

/**
 * Positive, persistent CAPTCHA/human-verification evidence after the complete
 * manual-verification window. It shares the scheduler's protective per-bot
 * cooldown path with other Vinted session blocks so the runtime does not launch
 * a new Chromium every minute while human action is still required.
 */
public class VintedHumanVerificationRequiredException
        extends VintedSessionBlockedException {

    public VintedHumanVerificationRequiredException(
            String message
    ) {
        super(message);
    }
}

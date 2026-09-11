package pl.flipbot.playwright.verification;

/**
 * Signals that a headless browser reached a visible human-verification page.
 *
 * <p>The scheduler catches this signal only after the current browser context
 * has saved its bot-specific storage state and closed. It can then open one
 * headed recovery window for the same bot without using the same session file
 * concurrently in two browser contexts.</p>
 */
public class HumanVerificationRequiredException extends RuntimeException {

    private final String challengeUrl;
    private final String evidence;

    public HumanVerificationRequiredException(
            String challengeUrl,
            String evidence
    ) {
        super(
                "Visible human verification requires manual completion. evidence="
                        + normalize(evidence)
        );
        this.challengeUrl = normalize(challengeUrl);
        this.evidence = normalize(evidence);
    }

    public String challengeUrl() {
        return challengeUrl;
    }

    public String evidence() {
        return evidence;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

package pl.flipbot.playwright.target;

import java.util.List;
import java.util.Locale;

/**
 * Classifies authentication failures that are unsafe to retry every minute even
 * when Vinted has not rendered the explicit hard-block copy quickly enough for
 * {@link VintedSessionBlockDetector} to read it.
 *
 * The signatures here are intentionally narrow. Explicit credential errors,
 * ordinary navigation failures and unrelated Playwright errors must continue to
 * use the normal failure path.
 */
public final class VintedSessionFailureClassifier {

    private static final List<String> PROTECTIVE_COOLDOWN_SIGNATURES = List.of(
            "vinted login form accepted three submit mechanisms without producing any observable authentication transition",
            "vinted login submission could not be verified"
    );

    private VintedSessionFailureClassifier() {
    }

    public static boolean shouldUseProtectiveCooldown(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(Locale.ROOT);
                for (String signature : PROTECTIVE_COOLDOWN_SIGNATURES) {
                    if (normalized.contains(signature)) {
                        return true;
                    }
                }
            }

            current = current.getCause();
        }

        return false;
    }
}

package pl.flipbot.playwright.verification;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
public class HumanVerificationHandler {

    private static final double VERIFICATION_TIMEOUT_MS =
            180_000;

    private static final double POLL_INTERVAL_MS =
            1_000;

    private static final double LOG_INTERVAL_MS =
            15_000;

    private static final List<String> VERIFICATION_TEXTS =
            List.of(
                    "sprawdzanie, czy jesteś człowiekiem",
                    "sprawdzanie czy jesteś człowiekiem",
                    "potwierdź, że jesteś człowiekiem",
                    "potwierdz, ze jestes czlowiekiem",
                    "verify you are human",
                    "verify that you are human",
                    "checking if you are human",
                    "checking your browser",
                    "security check",
                    "just a moment"
            );

    public void waitUntilVerified(
            Page page
    ) {
        Objects.requireNonNull(
                page,
                "Page cannot be null"
        );

        if (!isHumanVerificationVisible(page)) {
            return;
        }

        log.warn(
                "Human verification detected from positive page evidence. "
                        + "Bot actions are paused. Complete the verification manually if required."
        );

        double startedAt = System.currentTimeMillis();
        double deadline = startedAt + VERIFICATION_TIMEOUT_MS;
        double nextLogTime = startedAt + LOG_INTERVAL_MS;

        while (System.currentTimeMillis() < deadline) {
            if (page.isClosed()) {
                throw new IllegalStateException(
                        "Browser page was closed during human verification"
                );
            }

            page.waitForTimeout(POLL_INTERVAL_MS);

            if (!isHumanVerificationVisible(page)) {
                log.info(
                        "Human verification evidence disappeared. Bot may continue."
                );
                return;
            }

            double currentTime = System.currentTimeMillis();

            if (currentTime >= nextLogTime) {
                long elapsedSeconds = Math.round(
                        (currentTime - startedAt) / 1_000
                );

                log.warn(
                        "Still waiting for human verification. Elapsed time: {} seconds",
                        elapsedSeconds
                );

                nextLogTime = currentTime + LOG_INTERVAL_MS;
            }
        }

        throw new IllegalStateException(
                "Human verification was not completed within "
                        + Math.round(VERIFICATION_TIMEOUT_MS / 1_000)
                        + " seconds"
        );
    }

    public boolean isHumanVerificationVisible(
            Page page
    ) {
        Objects.requireNonNull(
                page,
                "Page cannot be null"
        );

        if (page.isClosed()) {
            return false;
        }

        try {
            if (containsVerificationIframe(page)) {
                return true;
            }

            String title = safeLower(page.title());
            String bodyText = safeLower(
                    page.locator("body").innerText()
            );

            return containsVerificationText(title)
                    || containsVerificationText(bodyText);

        } catch (PlaywrightException exception) {
            /*
             * A DOM read failing while Chromium is navigating is NOT evidence
             * of a CAPTCHA/challenge. The previous implementation returned
             * true here, which could produce false "human verification"
             * diagnoses during ordinary page transitions.
             */
            log.debug(
                    "Page changed while probing for human verification. Probe is inconclusive; no verification is reported without positive evidence."
            );
            return false;
        }
    }

    private boolean containsVerificationIframe(
            Page page
    ) {
        return page.locator(
                        "iframe[src*='challenges.cloudflare.com'], "
                                + "iframe[src*='challenge-platform'], "
                                + "iframe[src*='hcaptcha.com'], "
                                + "iframe[src*='recaptcha'], "
                                + "iframe[src*='turnstile']"
                )
                .count() > 0;
    }

    private boolean containsVerificationText(
            String text
    ) {
        if (text == null || text.isBlank()) {
            return false;
        }

        for (String verificationText : VERIFICATION_TEXTS) {
            if (text.contains(verificationText)) {
                return true;
            }
        }

        return false;
    }

    private String safeLower(String text) {
        return text == null
                ? ""
                : text.toLowerCase(Locale.ROOT);
    }
}

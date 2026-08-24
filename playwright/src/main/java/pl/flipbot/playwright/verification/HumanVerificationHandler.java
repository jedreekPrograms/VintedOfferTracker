package pl.flipbot.playwright.verification;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
public class HumanVerificationHandler {

    private static final String TIMEOUT_SECONDS_ENV =
            "FLIPBOT_HUMAN_VERIFICATION_TIMEOUT_SECONDS";

    private static final long DEFAULT_VERIFICATION_TIMEOUT_SECONDS = 600;
    private static final long MIN_VERIFICATION_TIMEOUT_SECONDS = 60;
    private static final long MAX_VERIFICATION_TIMEOUT_SECONDS = 1_800;

    private static final double POLL_INTERVAL_MS = 1_000;
    private static final double LOG_INTERVAL_MS = 15_000;

    /*
     * Do not interact with CAPTCHA / slider controls automatically.
     * This class only detects positive visible evidence, pauses bot actions,
     * waits for the user to complete the check manually and resumes only after
     * the challenge has stayed gone for several consecutive polls.
     */
    private static final int CLEAR_POLLS_REQUIRED = 3;

    private static final List<String> STRONG_VERIFICATION_TEXTS =
            List.of(
                    "sprawdzanie, czy jesteś człowiekiem",
                    "sprawdzanie czy jesteś człowiekiem",
                    "potwierdź, że jesteś człowiekiem",
                    "potwierdz, ze jestes czlowiekiem",
                    "przesuń w prawo, aby zabezpieczyć dostęp",
                    "przesun w prawo, aby zabezpieczyc dostep",
                    "verify you are human",
                    "verify that you are human",
                    "checking if you are human",
                    "checking your browser"
            );

    private static final List<String> VERIFICATION_TITLE_TEXTS =
            List.of(
                    "just a moment",
                    "security check"
            );

    private static final String VERIFICATION_IFRAME_SELECTOR =
            "iframe[src*='challenges.cloudflare.com'], "
                    + "iframe[src*='challenge-platform'], "
                    + "iframe[src*='hcaptcha.com'], "
                    + "iframe[src*='recaptcha'], "
                    + "iframe[src*='turnstile']";

    public void waitUntilVerified(
            Page page
    ) {
        Objects.requireNonNull(
                page,
                "Page cannot be null"
        );

        String evidence = verificationEvidence(page);

        if (evidence == null) {
            return;
        }

        long timeoutSeconds = verificationTimeoutSeconds();

        log.warn(
                "Human verification detected from visible positive page evidence. "
                        + "evidence={}. Bot actions are paused for up to {} seconds. "
                        + "Complete the verification manually in the open browser window.",
                evidence,
                timeoutSeconds
        );

        long startedAt = System.currentTimeMillis();
        long deadline = startedAt + timeoutSeconds * 1_000L;
        long nextLogTime = startedAt + (long) LOG_INTERVAL_MS;
        String latestEvidence = evidence;
        int clearPolls = 0;

        while (System.currentTimeMillis() < deadline) {
            if (page.isClosed()) {
                throw new IllegalStateException(
                        "Browser page was closed during human verification"
                );
            }

            page.waitForTimeout(POLL_INTERVAL_MS);

            latestEvidence = verificationEvidence(page);

            if (latestEvidence == null) {
                clearPolls++;

                if (clearPolls >= CLEAR_POLLS_REQUIRED) {
                    log.info(
                            "Human verification evidence stayed absent for {} consecutive checks. Bot may continue.",
                            CLEAR_POLLS_REQUIRED
                    );
                    return;
                }
            } else {
                clearPolls = 0;
            }

            long currentTime = System.currentTimeMillis();

            if (currentTime >= nextLogTime) {
                long elapsedSeconds = Math.round(
                        (currentTime - startedAt) / 1_000.0
                );

                log.warn(
                        "Still waiting for manual human verification. Elapsed time: {} seconds, evidence={}, clearPolls={}/{}.",
                        elapsedSeconds,
                        latestEvidence == null ? "temporarily absent" : latestEvidence,
                        clearPolls,
                        CLEAR_POLLS_REQUIRED
                );

                nextLogTime = currentTime + (long) LOG_INTERVAL_MS;
            }
        }

        throw new IllegalStateException(
                "Human verification was not completed within "
                        + timeoutSeconds
                        + " seconds. Last evidence: "
                        + latestEvidence
        );
    }

    public boolean isHumanVerificationVisible(
            Page page
    ) {
        Objects.requireNonNull(
                page,
                "Page cannot be null"
        );

        return verificationEvidence(page) != null;
    }

    String verificationEvidence(
            Page page
    ) {
        if (page.isClosed()) {
            return null;
        }

        try {
            String iframeEvidence = renderedVerificationIframeEvidence(page);

            if (iframeEvidence != null) {
                return iframeEvidence;
            }

            String title = safeLower(page.title());
            String titleMatch = matchingStrongText(title);

            if (titleMatch != null) {
                return "page title contains '" + titleMatch + "'";
            }

            String genericTitleMatch = matchingTitleOnlyText(title);

            if (genericTitleMatch != null) {
                return "page title contains challenge marker '"
                        + genericTitleMatch
                        + "'";
            }

            /*
             * innerText() represents rendered text, unlike textContent() which
             * also sees hidden templates. The slider screen shown by Vinted is
             * covered by the explicit Polish phrases above.
             */
            String bodyText = safeLower(
                    page.locator("body").innerText()
            );
            String bodyMatch = matchingStrongText(bodyText);

            if (bodyMatch != null) {
                return "visible body text contains '" + bodyMatch + "'";
            }

            return null;

        } catch (PlaywrightException exception) {
            log.debug(
                    "Page changed while probing for human verification. "
                            + "Probe is inconclusive; no verification is reported without positive evidence."
            );
            return null;
        }
    }

    private String renderedVerificationIframeEvidence(
            Page page
    ) {
        Locator iframes = page.locator(VERIFICATION_IFRAME_SELECTOR);
        int count = iframes.count();

        for (int index = 0; index < count; index++) {
            Locator iframe = iframes.nth(index);

            try {
                if (!iframe.isVisible()) {
                    continue;
                }

                Object result = iframe.evaluate(
                        """
                        element => {
                          const rect = element.getBoundingClientRect();
                          const style = window.getComputedStyle(element);
                          return rect.width >= 100
                              && rect.height >= 40
                              && style.display !== 'none'
                              && style.visibility !== 'hidden'
                              && Number(style.opacity || '1') > 0;
                        }
                        """
                );

                if (!(result instanceof Boolean rendered) || !rendered) {
                    continue;
                }

                String src = iframe.getAttribute("src");
                String safeSrc = src == null || src.isBlank()
                        ? "unknown-src"
                        : abbreviate(src, 180);

                return "rendered challenge iframe " + safeSrc;

            } catch (PlaywrightException exception) {
                log.debug(
                        "Verification iframe changed while its visibility was being inspected."
                );
            }
        }

        return null;
    }

    static String matchingStrongText(
            String text
    ) {
        return matchingText(
                safeLowerStatic(text),
                STRONG_VERIFICATION_TEXTS
        );
    }

    static String matchingTitleOnlyText(
            String text
    ) {
        return matchingText(
                safeLowerStatic(text),
                VERIFICATION_TITLE_TEXTS
        );
    }

    static long verificationTimeoutSeconds() {
        String raw = System.getenv(TIMEOUT_SECONDS_ENV);

        if (raw == null || raw.isBlank()) {
            return DEFAULT_VERIFICATION_TIMEOUT_SECONDS;
        }

        try {
            long parsed = Long.parseLong(raw.trim());
            return Math.max(
                    MIN_VERIFICATION_TIMEOUT_SECONDS,
                    Math.min(MAX_VERIFICATION_TIMEOUT_SECONDS, parsed)
            );
        } catch (NumberFormatException exception) {
            log.warn(
                    "Invalid {}='{}'. Using default {} seconds.",
                    TIMEOUT_SECONDS_ENV,
                    raw,
                    DEFAULT_VERIFICATION_TIMEOUT_SECONDS
            );
            return DEFAULT_VERIFICATION_TIMEOUT_SECONDS;
        }
    }

    private static String matchingText(
            String normalizedText,
            List<String> candidates
    ) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return null;
        }

        for (String candidate : candidates) {
            if (normalizedText.contains(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private static String safeLowerStatic(String text) {
        return text == null
                ? ""
                : text.toLowerCase(Locale.ROOT);
    }

    private String safeLower(String text) {
        return safeLowerStatic(text);
    }

    private String abbreviate(
            String text,
            int maxLength
    ) {
        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
}

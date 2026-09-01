package pl.flipbot.playwright.verification;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.target.VintedSessionBlockDetector;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
public class HumanVerificationHandler {

    private static final double VERIFICATION_TIMEOUT_MS = 180_000;
    private static final double POLL_INTERVAL_MS = 1_000;
    private static final double LOG_INTERVAL_MS = 15_000;

    private static final List<String> STRONG_VERIFICATION_TEXTS = List.of(
            "sprawdzanie, czy jesteś człowiekiem",
            "sprawdzanie czy jesteś człowiekiem",
            "potwierdź, że jesteś człowiekiem",
            "potwierdz, ze jestes czlowiekiem",
            "verify you are human",
            "verify that you are human",
            "checking if you are human",
            "checking your browser"
    );

    private static final List<String> VERIFICATION_TITLE_TEXTS = List.of(
            "just a moment",
            "security check"
    );

    private static final String VERIFICATION_IFRAME_SELECTOR =
            "iframe[src*='challenges.cloudflare.com'], "
                    + "iframe[src*='challenge-platform'], "
                    + "iframe[src*='hcaptcha.com'], "
                    + "iframe[src*='recaptcha'], "
                    + "iframe[src*='turnstile']";

    private final CookieConsentHandler cookieConsentHandler = new CookieConsentHandler();
    private final VintedSessionBlockDetector sessionBlockDetector =
            new VintedSessionBlockDetector();

    public void waitUntilVerified(Page page) {
        Objects.requireNonNull(page, "Page cannot be null");

        /* A hard Vinted session/IP block is not a CAPTCHA. Do not sit on it for
         * three minutes: surface it immediately so the scheduler can apply the
         * persistent per-bot exponential cooldown. */
        sessionBlockDetector.throwIfBlocked(page, "checking verification state");

        cookieConsentHandler.acceptAllIfVisible(page);
        sessionBlockDetector.throwIfBlocked(page, "after clearing cookie consent");

        String evidence = verificationEvidence(page);
        if (evidence == null) {
            return;
        }

        log.warn(
                "Human verification detected from visible positive page evidence. evidence={}. Bot actions are paused. Complete the verification manually if required.",
                evidence
        );

        double startedAt = System.currentTimeMillis();
        double deadline = startedAt + VERIFICATION_TIMEOUT_MS;
        double nextLogTime = startedAt + LOG_INTERVAL_MS;
        String latestEvidence = evidence;

        while (System.currentTimeMillis() < deadline) {
            if (page.isClosed()) {
                throw new IllegalStateException("Browser page was closed during human verification");
            }

            page.waitForTimeout(POLL_INTERVAL_MS);
            sessionBlockDetector.throwIfBlocked(page, "waiting for human verification");
            cookieConsentHandler.acceptAllIfVisible(page);
            sessionBlockDetector.throwIfBlocked(page, "after verification-page consent check");

            latestEvidence = verificationEvidence(page);
            if (latestEvidence == null) {
                log.info("Human verification evidence disappeared. Bot may continue.");
                return;
            }

            double currentTime = System.currentTimeMillis();
            if (currentTime >= nextLogTime) {
                long elapsedSeconds = Math.round((currentTime - startedAt) / 1_000);
                log.warn(
                        "Still waiting for human verification. Elapsed time: {} seconds, evidence={}.",
                        elapsedSeconds,
                        latestEvidence
                );
                nextLogTime = currentTime + LOG_INTERVAL_MS;
            }
        }

        throw new IllegalStateException(
                "Human verification was not completed within "
                        + Math.round(VERIFICATION_TIMEOUT_MS / 1_000)
                        + " seconds. Last evidence: "
                        + latestEvidence
        );
    }

    public boolean isHumanVerificationVisible(Page page) {
        Objects.requireNonNull(page, "Page cannot be null");
        sessionBlockDetector.throwIfBlocked(page, "probing human verification");
        return verificationEvidence(page) != null;
    }

    String verificationEvidence(Page page) {
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
                return "page title contains challenge marker '" + genericTitleMatch + "'";
            }

            String bodyText = safeLower(page.locator("body").innerText());
            String bodyMatch = matchingStrongText(bodyText);
            if (bodyMatch != null) {
                return "visible body text contains '" + bodyMatch + "'";
            }

            return null;
        } catch (PlaywrightException exception) {
            log.debug(
                    "Page changed while probing for human verification. Probe is inconclusive; no verification is reported without positive evidence."
            );
            return null;
        }
    }

    private String renderedVerificationIframeEvidence(Page page) {
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
                log.debug("Verification iframe changed while its visibility was being inspected.");
            }
        }

        return null;
    }

    static String matchingStrongText(String text) {
        return matchingText(safeLowerStatic(text), STRONG_VERIFICATION_TEXTS);
    }

    static String matchingTitleOnlyText(String text) {
        return matchingText(safeLowerStatic(text), VERIFICATION_TITLE_TEXTS);
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
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private String safeLower(String text) {
        return safeLowerStatic(text);
    }

    private String abbreviate(String text, int maxLength) {
        if (text.length() <= maxLength) {
            return text;
        }

        return text.substring(0, maxLength) + "...";
    }
}

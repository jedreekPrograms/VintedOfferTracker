package pl.flipbot.playwright.verification;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.runtime.CaptchaControlClient;
import pl.flipbot.playwright.target.VintedSessionBlockDetector;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Slf4j
public class HumanVerificationHandler {

    private static final double VERIFICATION_TIMEOUT_MS = 180_000;
    private static final double POLL_INTERVAL_MS = 1_000;
    private static final double MOBILE_POLL_INTERVAL_MS = 120;
    private static final double LOG_INTERVAL_MS = 15_000;
    private static final int CLEAR_POLLS_REQUIRED = 3;

    private static final List<String> STRONG_VERIFICATION_TEXTS = List.of(
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

    private static final List<String> VERIFICATION_TITLE_TEXTS = List.of(
            "just a moment",
            "security check"
    );

    private static final String VERIFICATION_IFRAME_SELECTOR =
            "iframe[src*='challenges.cloudflare.com'], "
                    + "iframe[src*='challenge-platform'], "
                    + "iframe[src*='hcaptcha.com'], "
                    + "iframe[src*='recaptcha'], "
                    + "iframe[src*='turnstile'], "
                    + "iframe[src*='captcha-delivery.com/captcha'], "
                    + "iframe[id^='ddChallengeBody'], "
                    + "iframe[title='Verification system']";

    private static final String VERIFICATION_CONTAINER_SELECTOR =
            "div[id^='ddChallengeContainer']";

    private static final String HEADLESS_RUNTIME_EXPRESSION =
            "() => globalThis.__flipbotBrowserHeadless === true";

    private final CookieConsentHandler cookieConsentHandler = new CookieConsentHandler();
    private final VintedSessionBlockDetector sessionBlockDetector =
            new VintedSessionBlockDetector();
    private final Long mobileControlBotId;
    private final CaptchaControlClient captchaControlClient;

    public HumanVerificationHandler() {
        this(null, null);
    }

    public HumanVerificationHandler(Long mobileControlBotId) {
        this(
                Objects.requireNonNull(
                        mobileControlBotId,
                        "Mobile CAPTCHA control bot ID cannot be null"
                ),
                new CaptchaControlClient()
        );

        if (mobileControlBotId <= 0L) {
            throw new IllegalArgumentException(
                    "Mobile CAPTCHA control requires a positive bot ID"
            );
        }
    }

    HumanVerificationHandler(
            Long mobileControlBotId,
            CaptchaControlClient captchaControlClient
    ) {
        this.mobileControlBotId = mobileControlBotId;
        this.captchaControlClient = captchaControlClient;
    }

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

        if (isHeadlessRuntime(page)) {
            log.warn(
                    "[CAPTCHA] Visible human verification detected in a headless browser. "
                            + "The current context will save its session and close before a manual headed recovery window is opened. evidence={}",
                    evidence
            );
            throw new HumanVerificationRequiredException(
                    safePageUrl(page),
                    evidence
            );
        }

        waitUntilManuallyVerified(
                page,
                (long) VERIFICATION_TIMEOUT_MS,
                evidence
        );
    }

    public void waitUntilManuallyVerified(
            Page page,
            long timeoutMs
    ) {
        Objects.requireNonNull(page, "Page cannot be null");

        if (timeoutMs <= 0L) {
            throw new IllegalArgumentException(
                    "Manual verification timeout must be positive"
            );
        }

        sessionBlockDetector.throwIfBlocked(
                page,
                "starting manual human verification"
        );
        cookieConsentHandler.acceptAllIfVisible(page);
        sessionBlockDetector.throwIfBlocked(
                page,
                "after manual verification consent check"
        );

        String evidence = verificationEvidence(page);
        if (evidence == null) {
            log.info(
                    "[CAPTCHA] The challenge did not reappear in the visible recovery window. The refreshed session may continue."
            );
            return;
        }

        waitUntilManuallyVerified(page, timeoutMs, evidence);
    }

    private void waitUntilManuallyVerified(
            Page page,
            long timeoutMs,
            String initialEvidence
    ) {

        log.warn(
                "[CAPTCHA] Human verification is visible. Complete it manually in the opened browser window. evidence={}",
                initialEvidence
        );

        MobileCaptchaDragController mobileControl = createMobileControl(page);
        if (mobileControl != null) {
            mobileControl.markReady();
        }

        double startedAt = System.currentTimeMillis();
        double deadline = startedAt + timeoutMs;
        double nextLogTime = startedAt + LOG_INTERVAL_MS;
        String latestEvidence = initialEvidence;
        String lastPositiveEvidence = initialEvidence;
        int clearPolls = 0;

        try {
            while (System.currentTimeMillis() < deadline) {
                if (page.isClosed()) {
                    throw new IllegalStateException("Browser page was closed during human verification");
                }

                page.waitForTimeout(
                        mobileControl == null
                                ? POLL_INTERVAL_MS
                                : MOBILE_POLL_INTERVAL_MS
                );

                if (mobileControl != null) {
                    mobileControl.tick();
                }

                sessionBlockDetector.throwIfBlocked(page, "waiting for human verification");
                cookieConsentHandler.acceptAllIfVisible(page);
                sessionBlockDetector.throwIfBlocked(page, "after verification-page consent check");

                latestEvidence = verificationEvidence(page);
                if (latestEvidence == null) {
                    clearPolls++;

                    if (clearPolls >= CLEAR_POLLS_REQUIRED) {
                        if (mobileControl != null) {
                            mobileControl.complete();
                        }

                        log.info(
                                "[CAPTCHA] Human verification evidence stayed absent for {} consecutive checks. The bot session may continue.",
                                CLEAR_POLLS_REQUIRED
                        );
                        return;
                    }
                } else {
                    lastPositiveEvidence = latestEvidence;
                    clearPolls = 0;
                }

                double currentTime = System.currentTimeMillis();
                if (currentTime >= nextLogTime) {
                    long elapsedSeconds = Math.round((currentTime - startedAt) / 1_000);
                    log.warn(
                            "Still waiting for human verification. Elapsed time: {} seconds, evidence={}, clearPolls={}/{}.",
                            elapsedSeconds,
                            latestEvidence == null
                                    ? "temporarily absent"
                                    : latestEvidence,
                            clearPolls,
                            CLEAR_POLLS_REQUIRED
                    );
                    nextLogTime = currentTime + LOG_INTERVAL_MS;
                }
            }
        } catch (RuntimeException exception) {
            if (mobileControl != null) {
                mobileControl.fail();
            }
            throw exception;
        }

        if (mobileControl != null) {
            mobileControl.fail();
        }

        throw new IllegalStateException(
                "Human verification was not completed within "
                        + Math.round(timeoutMs / 1_000.0)
                        + " seconds. Last evidence: "
                        + lastPositiveEvidence
        );
    }

    private MobileCaptchaDragController createMobileControl(Page page) {
        if (mobileControlBotId == null || captchaControlClient == null) {
            return null;
        }

        return new MobileCaptchaDragController(
                page,
                mobileControlBotId,
                captchaControlClient
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
            String containerEvidence = renderedVerificationContainerEvidence(page);
            if (containerEvidence != null) {
                return containerEvidence;
            }

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

    private String renderedVerificationContainerEvidence(Page page) {
        Locator containers = page.locator(VERIFICATION_CONTAINER_SELECTOR);
        int count = containers.count();

        for (int index = 0; index < count; index++) {
            if (isRendered(containers.nth(index), 50, 30)) {
                return "rendered DataDome challenge container";
            }
        }

        return null;
    }

    private String renderedVerificationIframeEvidence(Page page) {
        Locator iframes = page.locator(VERIFICATION_IFRAME_SELECTOR);
        int count = iframes.count();

        for (int index = 0; index < count; index++) {
            Locator iframe = iframes.nth(index);
            try {
                if (!isRendered(iframe, 100, 40)) {
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

    private boolean isRendered(
            Locator locator,
            int minimumWidth,
            int minimumHeight
    ) {
        try {
            if (!locator.isVisible()) {
                return false;
            }

            Object result = locator.evaluate(
                    """
                    (element, dimensions) => {
                      const rect = element.getBoundingClientRect();
                      const style = window.getComputedStyle(element);
                      return rect.width >= dimensions.minimumWidth
                          && rect.height >= dimensions.minimumHeight
                          && style.display !== 'none'
                          && style.visibility !== 'hidden'
                          && Number(style.opacity || '1') > 0;
                    }
                    """,
                    java.util.Map.of(
                            "minimumWidth", minimumWidth,
                            "minimumHeight", minimumHeight
                    )
            );

            return result instanceof Boolean rendered && rendered;
        } catch (PlaywrightException exception) {
            log.debug(
                    "Verification element changed while its visibility was being inspected."
            );
            return false;
        }
    }

    private boolean isHeadlessRuntime(Page page) {
        try {
            Object result = page.evaluate(HEADLESS_RUNTIME_EXPRESSION);
            return result instanceof Boolean headless && headless;
        } catch (PlaywrightException exception) {
            log.debug(
                    "Could not read the FlipBot browser-mode marker while handling human verification. Keeping the current browser open for manual completion."
            );
            return false;
        }
    }

    private String safePageUrl(Page page) {
        try {
            String url = page.url();
            return url == null ? "" : url;
        } catch (PlaywrightException exception) {
            return "";
        }
    }

    static String matchingStrongText(String text) {
        return matchingText(safeLowerStatic(text), STRONG_VERIFICATION_TEXTS);
    }

    static String matchingTitleOnlyText(String text) {
        return matchingText(safeLowerStatic(text), VERIFICATION_TITLE_TEXTS);
    }

    static String verificationIframeSelector() {
        return VERIFICATION_IFRAME_SELECTOR;
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

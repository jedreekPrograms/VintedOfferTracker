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
                    "verify you are human",
                    "checking if you are human",
                    "checking your browser",
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
                "Human verification detected. "
                        + "Bot actions are paused. "
                        + "Complete the verification manually if required."
        );

        double startedAt =
                System.currentTimeMillis();

        double deadline =
                startedAt
                        + VERIFICATION_TIMEOUT_MS;

        double nextLogTime =
                startedAt
                        + LOG_INTERVAL_MS;

        while (System.currentTimeMillis() < deadline) {

            if (page.isClosed()) {

                throw new IllegalStateException(
                        "Browser page was closed during human verification"
                );

            }

            page.waitForTimeout(
                    POLL_INTERVAL_MS
            );

            if (!isHumanVerificationVisible(page)) {

                log.info(
                        "Human verification completed. "
                                + "Bot may continue."
                );

                return;

            }

            double currentTime =
                    System.currentTimeMillis();

            if (currentTime >= nextLogTime) {

                long elapsedSeconds =
                        Math.round(
                                (currentTime - startedAt)
                                        / 1_000
                        );

                log.warn(
                        "Still waiting for human verification. "
                                + "Elapsed time: {} seconds",
                        elapsedSeconds
                );

                nextLogTime =
                        currentTime
                                + LOG_INTERVAL_MS;

            }

        }

        throw new IllegalStateException(
                "Human verification was not completed within "
                        + Math.round(
                        VERIFICATION_TIMEOUT_MS / 1_000
                )
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

            String title =
                    page.title()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            String bodyText =
                    page.locator("body")
                            .innerText()
                            .toLowerCase(
                                    Locale.ROOT
                            );

            if (containsVerificationText(title)) {
                return true;
            }

            if (containsVerificationText(bodyText)) {
                return true;
            }

            return bodyText.contains(
                    "please wait"
            )
                    && bodyText.contains(
                    "człowiekiem"
            );

        } catch (PlaywrightException exception) {

            log.debug(
                    "Page is changing while checking human verification",
                    exception
            );

            return true;

        }

    }

    private boolean containsVerificationIframe(
            Page page
    ) {

        return page.locator(
                        "iframe[src*='challenges.cloudflare.com'], "
                                + "iframe[src*='challenge-platform']"
                )
                .count() > 0;

    }

    private boolean containsVerificationText(
            String text
    ) {

        if (text == null
                || text.isBlank()) {

            return false;

        }

        for (String verificationText
                : VERIFICATION_TEXTS) {

            if (text.contains(
                    verificationText
            )) {

                return true;

            }

        }

        return false;

    }

}
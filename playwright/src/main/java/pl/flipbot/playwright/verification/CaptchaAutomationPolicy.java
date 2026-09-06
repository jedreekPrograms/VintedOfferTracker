package pl.flipbot.playwright.verification;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

@Slf4j
public final class CaptchaAutomationPolicy {

    public static final String ENV_NAME = "FLIPBOT_CAPTCHA_AUTOMATION_ENABLED";
    private static final boolean DEFAULT_ENABLED = false;

    private CaptchaAutomationPolicy() {
    }

    public static boolean enabled() {
        String raw = System.getenv(ENV_NAME);

        if (raw == null || raw.isBlank()) {
            return DEFAULT_ENABLED;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }

        log.warn(
                "Invalid {}='{}'. Using default {}.",
                ENV_NAME,
                raw,
                DEFAULT_ENABLED
        );
        return DEFAULT_ENABLED;
    }
}

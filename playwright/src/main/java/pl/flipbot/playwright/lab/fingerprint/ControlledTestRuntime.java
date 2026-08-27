package pl.flipbot.playwright.lab.fingerprint;

import java.util.Locale;

/**
 * Three-variable entry point for the controlled test browser runtime.
 *
 * The simple mode intentionally keeps the production boundary in
 * {@link FingerprintLabPolicy}. It only removes configuration ceremony: one
 * switch, one test URL and one fleet size are enough to activate the existing
 * fingerprint/session/behavior lab stack on loopback or reserved .test hosts.
 */
public final class ControlledTestRuntime {

    public static final String ENABLE_ENV = "FLIPBOT_TEST_AUTOMATION";
    public static final String TARGET_URL_ENV = "FLIPBOT_TEST_URL";
    public static final String BOT_COUNT_ENV = "FLIPBOT_TEST_BOTS";

    public static final int DEFAULT_BOT_COUNT = 350;
    public static final int MAX_BOT_COUNT = 350;

    private ControlledTestRuntime() {}

    public static boolean isEnabled() {
        return readBoolean(ENABLE_ENV, false);
    }

    public static String targetUrl() {
        String raw = System.getenv(TARGET_URL_ENV);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim();
    }

    public static int botCount() {
        String raw = System.getenv(BOT_COUNT_ENV);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_BOT_COUNT;
        }

        final int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    BOT_COUNT_ENV + " must be an integer between 1 and " + MAX_BOT_COUNT,
                    exception
            );
        }

        if (parsed < 1 || parsed > MAX_BOT_COUNT) {
            throw new IllegalArgumentException(
                    BOT_COUNT_ENV + " must be between 1 and " + MAX_BOT_COUNT
            );
        }

        return parsed;
    }

    public static void requireValidConfiguration() {
        if (!isEnabled()) {
            return;
        }

        String targetUrl = targetUrl();
        if (targetUrl == null) {
            throw new IllegalStateException(
                    ENABLE_ENV + "=true requires " + TARGET_URL_ENV
            );
        }

        if (!FingerprintLabPolicy.isAllowedUrl(targetUrl)) {
            throw new IllegalStateException(
                    "Controlled test runtime refuses target URL: "
                            + targetUrl
                            + ". Use loopback, *.localhost, test or *.test only."
            );
        }

        botCount();
    }

    public static boolean hasSessionEncryptionKey() {
        return hasText(System.getenv("FLIPBOT_SESSION_ENCRYPTION_KEY"))
                || hasText(System.getenv("FLIPBOT_ENCRYPTION_KEY"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean readBoolean(String name, boolean fallback) {
        String raw = System.getenv(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "true", "1", "yes", "y", "on" -> true;
            case "false", "0", "no", "n", "off" -> false;
            default -> fallback;
        };
    }
}

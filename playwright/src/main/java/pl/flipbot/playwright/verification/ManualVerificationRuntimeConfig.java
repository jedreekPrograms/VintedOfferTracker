package pl.flipbot.playwright.verification;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public record ManualVerificationRuntimeConfig(
        long timeoutSeconds
) {

    static final String TIMEOUT_SECONDS_ENV =
            "FLIPBOT_MANUAL_VERIFICATION_TIMEOUT_SECONDS";

    private static final long DEFAULT_TIMEOUT_SECONDS = 10L * 60L;
    private static final long MINIMUM_TIMEOUT_SECONDS = 30L;
    private static final long MAXIMUM_TIMEOUT_SECONDS = 60L * 60L;

    public static ManualVerificationRuntimeConfig fromEnvironment() {
        return fromRawValue(System.getenv(TIMEOUT_SECONDS_ENV));
    }

    static ManualVerificationRuntimeConfig fromRawValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return new ManualVerificationRuntimeConfig(
                    DEFAULT_TIMEOUT_SECONDS
            );
        }

        try {
            long parsed = Long.parseLong(rawValue.trim());

            if (parsed < MINIMUM_TIMEOUT_SECONDS
                    || parsed > MAXIMUM_TIMEOUT_SECONDS) {
                throw new IllegalArgumentException(
                        "Value is outside allowed range "
                                + MINIMUM_TIMEOUT_SECONDS
                                + ".."
                                + MAXIMUM_TIMEOUT_SECONDS
                );
            }

            return new ManualVerificationRuntimeConfig(parsed);
        } catch (RuntimeException exception) {
            log.warn(
                    "Invalid {}='{}'. Using default {} seconds.",
                    TIMEOUT_SECONDS_ENV,
                    rawValue,
                    DEFAULT_TIMEOUT_SECONDS
            );
            return new ManualVerificationRuntimeConfig(
                    DEFAULT_TIMEOUT_SECONDS
            );
        }
    }

    public long timeoutMillis() {
        return timeoutSeconds * 1_000L;
    }
}

package pl.flipbot.playwright.verification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ManualVerificationRuntimeConfigTest {

    @Test
    public void defaultsToTenMinutes() {
        assertEquals(
                600L,
                ManualVerificationRuntimeConfig.fromRawValue(null)
                        .timeoutSeconds()
        );
    }

    @Test
    public void acceptsTimeoutInsideAllowedRange() {
        ManualVerificationRuntimeConfig config =
                ManualVerificationRuntimeConfig.fromRawValue("900");

        assertEquals(900L, config.timeoutSeconds());
        assertEquals(900_000L, config.timeoutMillis());
    }

    @Test
    public void rejectsTimeoutOutsideAllowedRange() {
        assertEquals(
                600L,
                ManualVerificationRuntimeConfig.fromRawValue("29")
                        .timeoutSeconds()
        );
        assertEquals(
                600L,
                ManualVerificationRuntimeConfig.fromRawValue("3601")
                        .timeoutSeconds()
        );
        assertEquals(
                600L,
                ManualVerificationRuntimeConfig.fromRawValue("invalid")
                        .timeoutSeconds()
        );
    }
}

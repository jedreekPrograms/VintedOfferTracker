package pl.flipbot.playwright.lab.fingerprint;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FingerprintLabServerTest {

    @Test
    public void detectsNamedCookieWithoutExposingItsValue() {
        assertTrue(FingerprintLabServer.containsCookie(
                "theme=dark; fp_lab_session=abc123; locale=pl",
                "fp_lab_session"
        ));

        assertFalse(FingerprintLabServer.containsCookie(
                "theme=dark; locale=pl",
                "fp_lab_session"
        ));
        assertFalse(FingerprintLabServer.containsCookie(
                null,
                "fp_lab_session"
        ));
        assertFalse(FingerprintLabServer.containsCookie(
                "fp_lab_session_extra=abc123",
                "fp_lab_session"
        ));
    }
}

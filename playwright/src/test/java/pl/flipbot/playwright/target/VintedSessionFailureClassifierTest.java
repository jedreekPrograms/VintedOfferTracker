package pl.flipbot.playwright.target;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VintedSessionFailureClassifierTest {

    @Test
    public void repeatedNoTransitionLoginUsesProtectiveCooldown() {
        assertTrue(
                VintedSessionFailureClassifier.shouldUseProtectiveCooldown(
                        new IllegalStateException(
                                "Vinted login form accepted three submit mechanisms without producing any observable authentication transition. Refusing to pretend that login or human verification happened."
                        )
                )
        );
    }

    @Test
    public void postLoginVerificationTimeoutUsesProtectiveCooldown() {
        assertTrue(
                VintedSessionFailureClassifier.shouldUseProtectiveCooldown(
                        new RuntimeException(
                                "wrapper",
                                new IllegalStateException(
                                        "Vinted login submission could not be verified. Current URL: https://www.vinted.pl/member/login/email"
                                )
                        )
                )
        );
    }

    @Test
    public void explicitCredentialAndUnrelatedFailuresStayGeneric() {
        assertFalse(
                VintedSessionFailureClassifier.shouldUseProtectiveCooldown(
                        new IllegalStateException(
                                "Vinted rejected the login form: incorrect email or password"
                        )
                )
        );
        assertFalse(
                VintedSessionFailureClassifier.shouldUseProtectiveCooldown(
                        new IllegalStateException("catalog filter was not visible")
                )
        );
        assertFalse(VintedSessionFailureClassifier.shouldUseProtectiveCooldown(null));
    }
}

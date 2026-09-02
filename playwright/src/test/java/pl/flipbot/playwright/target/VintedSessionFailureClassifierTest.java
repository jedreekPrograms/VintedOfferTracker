package pl.flipbot.playwright.target;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class VintedSessionFailureClassifierTest {

    @Test
    public void repeatedNoTransitionLoginDoesNotClaimSessionBlock() {
        assertFalse(
                VintedSessionFailureClassifier.shouldUseProtectiveCooldown(
                        new IllegalStateException(
                                "Vinted login form accepted three submit mechanisms without producing any observable authentication transition. Refusing to pretend that login or human verification happened."
                        )
                )
        );
    }

    @Test
    public void postLoginVerificationTimeoutDoesNotClaimSessionBlock() {
        assertFalse(
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
    public void unrelatedFailuresStayGeneric() {
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

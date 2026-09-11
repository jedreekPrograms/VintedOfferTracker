package pl.flipbot.playwright.browser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class OneTrustConsentGuardTest {

    @Test
    public void guardCoversBannerAndPreferenceCenterAcceptControls() {
        String script = OneTrustConsentGuard.script();

        assertTrue(script.contains("onetrust-accept-btn-handler"));
        assertTrue(script.contains("accept-recommended-btn-handler"));
        assertTrue(script.contains(".onetrust-pc-dark-filter"));
    }

    @Test
    public void preferenceCenterRecoveryUsesOnlyKnownOneTrustExitControls() {
        String script = OneTrustConsentGuard.script();

        assertTrue(script.contains("save-preference-btn-handler"));
        assertTrue(script.contains("close-pc-btn-handler"));
        assertFalse(script.contains("document.body.remove"));
        assertFalse(script.contains("querySelectorAll(\"button\")"));
    }
}

package pl.flipbot.playwright.browser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VintedInformationalDialogGuardTest {

    @Test
    public void guardTargetsOnlyTheKnownElectronicsVerificationOverlay() {
        String script = VintedInformationalDialogGuard.script();

        assertTrue(
                script.contains(
                        "electronics-verification-pop-up-dialog--overlay"
                )
        );
        assertTrue(script.contains("weryfikacja elektroniki"));
        assertTrue(script.contains("electronics verification"));
    }

    @Test
    public void guardPrefersExactAcknowledgementLabels() {
        String script = VintedInformationalDialogGuard.script();

        assertTrue(script.contains("rozumiem"));
        assertTrue(script.contains("got it"));
        assertTrue(script.contains("i understand"));
    }

    @Test
    public void guardNeverSearchesEveryPageButtonForAClosingAction() {
        String script = VintedInformationalDialogGuard.script();

        assertTrue(script.contains("overlay.querySelectorAll(\"button\")"));
        assertFalse(script.contains("document.querySelectorAll(\"button\")"));
    }
}

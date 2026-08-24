package pl.flipbot.playwright.probe;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PriceProbeTestStealthProfileTest {

    @Test
    public void scopedScriptIsLockedToExactConfiguredOrigin() {
        String script = PriceProbeTestStealthProfile.scopedInitScript(
                URI.create("https://probe-test.example.com")
        );

        assertTrue(
                script.contains(
                        "const allowedOrigin = \"https://probe-test.example.com\""
                )
        );
        assertTrue(
                script.contains("location.origin !== allowedOrigin")
        );
        assertTrue(
                script.contains("Navigator.prototype")
        );
        assertTrue(script.contains("'webdriver'"));
        assertTrue(script.contains("'languages'"));
        assertTrue(script.contains("window.chrome"));
        assertTrue(script.contains("navigator.permissions.query"));
    }

    @Test
    public void localControlledOriginIsSupported() {
        String script = PriceProbeTestStealthProfile.scopedInitScript(
                URI.create("http://localhost:4173")
        );

        assertTrue(
                script.contains(
                        "const allowedOrigin = \"http://localhost:4173\""
                )
        );
    }

    @Test(expected = IllegalStateException.class)
    public void realVintedOriginIsRejectedBeforeScriptCreation() {
        PriceProbeTestStealthProfile.scopedInitScript(
                URI.create("https://www.vinted.pl")
        );
    }

    @Test
    public void scopedScriptDoesNotHardcodeVintedAsAllowedOrigin() {
        String script = PriceProbeTestStealthProfile.scopedInitScript(
                URI.create("https://probe-test.example.com")
        );

        assertFalse(
                script.contains(
                        "const allowedOrigin = \"https://www.vinted.pl\""
                )
        );
    }
}

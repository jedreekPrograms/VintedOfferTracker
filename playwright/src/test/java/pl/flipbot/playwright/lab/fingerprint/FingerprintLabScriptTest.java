package pl.flipbot.playwright.lab.fingerprint;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FingerprintLabScriptTest {

    @Test
    public void simulationCoversModernBrowserSurfacesAndStaysDetectable() {
        String script = FingerprintLabScript.build(
                FingerprintLabProfile.demoDesktopProfile()
        );

        assertTrue(script.contains("Navigator.prototype"));
        assertTrue(script.contains("hardwareConcurrency"));
        assertTrue(script.contains("deviceMemory"));
        assertTrue(script.contains("maxTouchPoints"));
        assertTrue(script.contains("screenPrototype"));
        assertTrue(script.contains("devicePixelRatio"));
        assertTrue(script.contains("WebGLRenderingContext"));
        assertTrue(script.contains("HTMLCanvasElement"));
        assertTrue(script.contains("__flipbotFingerprintLab"));
        assertTrue(script.contains("local-lab-only"));

        // The teaching simulator must not pretend patched functions are native.
        assertFalse(script.contains("Function.prototype.toString ="));
        assertFalse(script.contains("[native code]"));
    }
}

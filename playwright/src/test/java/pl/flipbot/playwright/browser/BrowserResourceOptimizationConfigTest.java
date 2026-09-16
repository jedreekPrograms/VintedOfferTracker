package pl.flipbot.playwright.browser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BrowserResourceOptimizationConfigTest {

    @Test
    public void headlessKeepsServiceWorkersEnabledByDefault() {
        assertFalse(BrowserResourceOptimizationConfig.blockServiceWorkers(
                true,
                null
        ));
        assertFalse(BrowserResourceOptimizationConfig.blockServiceWorkers(
                true,
                "   "
        ));
    }

    @Test
    public void headedBrowserNeverForcesServiceWorkerBlocking() {
        assertFalse(BrowserResourceOptimizationConfig.blockServiceWorkers(
                false,
                null
        ));
        assertFalse(BrowserResourceOptimizationConfig.blockServiceWorkers(
                false,
                "true"
        ));
    }

    @Test
    public void headlessOverrideCanKeepBlockingDisabled() {
        assertFalse(BrowserResourceOptimizationConfig.blockServiceWorkers(
                true,
                "false"
        ));
        assertFalse(BrowserResourceOptimizationConfig.blockServiceWorkers(
                true,
                "0"
        ));
        assertFalse(BrowserResourceOptimizationConfig.blockServiceWorkers(
                true,
                "off"
        ));
    }

    @Test
    public void headlessOverrideCanEnableBlockingAndInvalidValueFallsBackToDefault() {
        assertTrue(BrowserResourceOptimizationConfig.blockServiceWorkers(
                true,
                "yes"
        ));
        assertTrue(BrowserResourceOptimizationConfig.blockServiceWorkers(
                true,
                "true"
        ));
        assertFalse(BrowserResourceOptimizationConfig.blockServiceWorkers(
                true,
                "invalid-value"
        ));
    }

    @Test
    public void PlaywrightChromiumIsOptInForHeadlessOnly() {
        assertFalse(BrowserResourceOptimizationConfig.usePlaywrightChromium(
                true,
                null
        ));
        assertFalse(BrowserResourceOptimizationConfig.usePlaywrightChromium(
                true,
                "invalid-value"
        ));
        assertTrue(BrowserResourceOptimizationConfig.usePlaywrightChromium(
                true,
                "true"
        ));
        assertTrue(BrowserResourceOptimizationConfig.usePlaywrightChromium(
                true,
                "1"
        ));
        assertFalse(BrowserResourceOptimizationConfig.usePlaywrightChromium(
                false,
                "true"
        ));
    }
}

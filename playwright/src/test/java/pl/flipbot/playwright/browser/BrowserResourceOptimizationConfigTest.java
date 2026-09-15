package pl.flipbot.playwright.browser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BrowserResourceOptimizationConfigTest {

    @Test
    public void headlessBlocksServiceWorkersByDefault() {
        assertTrue(BrowserResourceOptimizationConfig.blockServiceWorkers(
                true,
                null
        ));
        assertTrue(BrowserResourceOptimizationConfig.blockServiceWorkers(
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
    public void headlessOverrideCanDisableBlocking() {
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
    public void headlessOverrideAcceptsEnabledValuesAndFailsSafeToDefault() {
        assertTrue(BrowserResourceOptimizationConfig.blockServiceWorkers(
                true,
                "yes"
        ));
        assertTrue(BrowserResourceOptimizationConfig.blockServiceWorkers(
                true,
                "invalid-value"
        ));
    }
}

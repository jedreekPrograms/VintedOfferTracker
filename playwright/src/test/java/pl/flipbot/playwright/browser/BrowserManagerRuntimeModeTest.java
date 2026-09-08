package pl.flipbot.playwright.browser;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class BrowserManagerRuntimeModeTest {

    @Test
    public void headlessContextReceivesTrueRuntimeMarker() {
        String script = BrowserManager.browserRuntimeModeScript(true);

        assertTrue(script.contains("__flipbotBrowserHeadless"));
        assertTrue(script.contains("value: true"));
        assertTrue(script.contains("writable: false"));
    }

    @Test
    public void headedContextReceivesFalseRuntimeMarker() {
        String script = BrowserManager.browserRuntimeModeScript(false);

        assertTrue(script.contains("__flipbotBrowserHeadless"));
        assertTrue(script.contains("value: false"));
    }
}

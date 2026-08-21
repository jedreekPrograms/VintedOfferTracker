package pl.flipbot.playwright.browser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class BrowserFactoryTest {

    @Test
    public void restoresChromeNativePopupBlockingWithoutDroppingOtherDefaults() {
        assertEquals(
                List.of("--disable-popup-blocking"),
                BrowserFactory.ignoredPlaywrightDefaultArgs()
        );
    }
}

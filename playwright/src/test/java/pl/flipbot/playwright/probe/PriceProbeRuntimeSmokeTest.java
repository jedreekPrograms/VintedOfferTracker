package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PriceProbeRuntimeSmokeTest {
    @Test
    public void configTypeExists() {
        assertNotNull(PriceProbeRuntimeConfig.class);
    }
}

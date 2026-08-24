package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PriceProbeUrlGuardSmokeTest {
    @Test
    public void runtimeConfigClassLoads() {
        assertNotNull(PriceProbeRuntimeConfig.class);
    }
}

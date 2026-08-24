package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PriceProbeStrictHostTest {
    @Test
    public void detectsVintedHost() {
        assertTrue(PriceProbeRuntimeConfig.isRealVintedHost("www.vinted.pl"));
    }
}

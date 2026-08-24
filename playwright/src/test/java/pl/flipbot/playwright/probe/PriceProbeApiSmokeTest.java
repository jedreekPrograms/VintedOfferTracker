package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PriceProbeApiSmokeTest {
    @Test
    public void apiClientTypeExists() {
        assertNotNull(PriceProbeApiClient.class);
    }
}

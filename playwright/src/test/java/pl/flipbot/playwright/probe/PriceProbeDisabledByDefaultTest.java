package pl.flipbot.playwright.probe;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertFalse;

public class PriceProbeDisabledByDefaultTest {

    @Test
    public void disabledConfigAllowsNoDestination() {
        PriceProbeRuntimeConfig config = new PriceProbeRuntimeConfig(
                false,
                URI.create("https://clone.example"),
                1
        );

        assertFalse(config.isAllowedUrl("https://clone.example/items/123"));
    }
}

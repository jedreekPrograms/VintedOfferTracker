package pl.flipbot.playwright.probe;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertFalse;

public class PriceProbeHostBlockTest {

    @Test
    public void realVintedUrlIsNeverAllowedAsProbeDestination() {
        PriceProbeRuntimeConfig config = new PriceProbeRuntimeConfig(
                true,
                URI.create("https://clone.example"),
                1
        );

        assertFalse(config.isAllowedUrl("https://www.vinted.pl/items/123"));
    }
}

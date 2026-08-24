package pl.flipbot.playwright.probe;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;

public class PriceProbeSandboxMappingTest {

    @Test
    public void sourceHostIsDiscardedDuringMapping() {
        PriceProbeRuntimeConfig config = new PriceProbeRuntimeConfig(
                true,
                URI.create("https://clone.example"),
                1
        );

        assertEquals(
                "https://clone.example/items/123?foo=bar",
                config.sandboxListingUrl("https://www.vinted.pl/items/123?foo=bar")
        );
    }
}

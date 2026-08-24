package pl.flipbot.playwright.probe;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;

public class PriceProbeMaxPerJobTest {

    @Test
    public void runtimeCarriesBoundedBatchSize() {
        PriceProbeRuntimeConfig config = new PriceProbeRuntimeConfig(
                true,
                URI.create("https://clone.example"),
                1
        );

        assertEquals(1, config.maxPerJob());
    }
}

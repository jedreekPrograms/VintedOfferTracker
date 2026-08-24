package pl.flipbot.playwright.probe;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;

public class PriceProbeCloneOnlyTest {

    @Test
    public void homeUrlUsesCloneBase() {
        PriceProbeRuntimeConfig config = new PriceProbeRuntimeConfig(
                true,
                URI.create("https://clone.example"),
                1
        );

        assertEquals("https://clone.example/", config.homeUrl());
    }
}

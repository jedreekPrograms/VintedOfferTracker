package pl.flipbot.playwright.probe;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PriceProbeTestHumanPacingTest {

    @Test
    public void pacingScopeAcceptsOnlyConfiguredProbeEndpoint() {
        PriceProbeRuntimeConfig config = new PriceProbeRuntimeConfig(
                true,
                URI.create("http://localhost:4173")
        );

        assertTrue(
                PriceProbeTestHumanPacing.allowedFor(
                        config,
                        "http://localhost:4173/items/123"
                )
        );

        assertFalse(
                PriceProbeTestHumanPacing.allowedFor(
                        config,
                        "http://localhost:4174/items/123"
                )
        );
        assertFalse(
                PriceProbeTestHumanPacing.allowedFor(
                        config,
                        "https://www.vinted.pl/items/123"
                )
        );
        assertFalse(
                PriceProbeTestHumanPacing.allowedFor(
                        config,
                        "https://example.com/items/123"
                )
        );
    }

    @Test
    public void pacingScopeRejectsDisabledProbeConfig() {
        PriceProbeRuntimeConfig config = new PriceProbeRuntimeConfig(
                false,
                URI.create("http://localhost:4173")
        );

        assertFalse(
                PriceProbeTestHumanPacing.allowedFor(
                        config,
                        "http://localhost:4173/items/123"
                )
        );
    }

    @Test
    public void pacingScopeRejectsDnsEquivalentVintedEndpointEvenWhenConstructedDirectly() {
        PriceProbeRuntimeConfig dottedVintedConfig =
                new PriceProbeRuntimeConfig(
                        true,
                        URI.create("https://www.vinted.pl.")
                );

        assertFalse(
                PriceProbeTestHumanPacing.allowedFor(
                        dottedVintedConfig,
                        "https://www.vinted.pl./items/123"
                )
        );
    }
}

package pl.flipbot.playwright.probe;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PriceProbeRuntimeConfigTest {

    @Test
    public void disabledFlagBlocksProbeUrls() {
        PriceProbeRuntimeConfig config = new PriceProbeRuntimeConfig(
                false,
                URI.create("http://localhost:4173")
        );

        assertFalse(config.isAllowedUrl("http://localhost:4173/items/1"));
    }

    @Test
    public void mapsSourcePathOntoConfiguredEndpointWhenEnabled() {
        PriceProbeRuntimeConfig config = new PriceProbeRuntimeConfig(
                true,
                URI.create("https://probe-test.example")
        );

        assertEquals(
                "https://probe-test.example/items/123-phone?ref=catalog",
                config.mappedListingUrl(
                        "https://source.example/items/123-phone?ref=catalog"
                )
        );
        assertTrue(config.isAllowedUrl(
                "https://probe-test.example/items/123-phone"
        ));
    }

    @Test
    public void realVintedHostRemainsOutsideProbeTestEndpoint() {
        assertThrows(
                IllegalStateException.class,
                () -> PriceProbeRuntimeConfig.validateBaseUrl(
                        "https://www.vinted.pl"
                )
        );
    }

    @Test
    public void dnsRootDotCannotBypassVintedProbeProhibition() {
        assertTrue(PriceProbeRuntimeConfig.isRealVintedHost("vinted.pl."));
        assertTrue(PriceProbeRuntimeConfig.isRealVintedHost("WWW.VINTED.PL."));
        assertTrue(PriceProbeRuntimeConfig.isRealVintedHost("sub.vinted.pl.."));

        assertThrows(
                IllegalStateException.class,
                () -> PriceProbeRuntimeConfig.validateBaseUrl(
                        "https://www.vinted.pl."
                )
        );
    }
}

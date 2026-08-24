package pl.flipbot.playwright.probe;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PriceProbeRuntimeConfigTest {

    @Test
    public void rejectsRealVintedHost() {
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> PriceProbeRuntimeConfig.validateSandboxBaseUrl(
                        "https://www.vinted.pl"
                )
        );

        assertTrue(exception.getMessage().contains("vinted.pl"));
    }

    @Test
    public void rejectsRealVintedSubdomain() {
        assertThrows(
                IllegalStateException.class,
                () -> PriceProbeRuntimeConfig.validateSandboxBaseUrl(
                        "https://sandbox.vinted.pl"
                )
        );
    }

    @Test
    public void acceptsHttpsCloneHost() {
        URI uri = PriceProbeRuntimeConfig.validateSandboxBaseUrl(
                "https://marketplace-test.example"
        );

        assertTrue(
                uri.toString().equals("https://marketplace-test.example")
        );
    }

    @Test
    public void acceptsHttpOnlyForLocalDevelopment() {
        assertTrue(
                PriceProbeRuntimeConfig.validateSandboxBaseUrl(
                        "http://localhost:4173"
                ).toString().equals("http://localhost:4173")
        );

        assertThrows(
                IllegalStateException.class,
                () -> PriceProbeRuntimeConfig.validateSandboxBaseUrl(
                        "http://marketplace-test.example"
                )
        );
    }

    @Test
    public void allowedUrlMustStayOnConfiguredCloneEndpoint() {
        PriceProbeRuntimeConfig config =
                new PriceProbeRuntimeConfig(
                        true,
                        URI.create("https://clone.example"),
                        1
                );

        assertTrue(config.isAllowedUrl(
                "https://clone.example/items/123-samsung"
        ));
        assertFalse(config.isAllowedUrl(
                "https://other.example/items/123-samsung"
        ));
        assertFalse(config.isAllowedUrl(
                "https://www.vinted.pl/items/123-samsung"
        ));
    }

    @Test
    public void lookalikeDomainIsNotMistakenForRealVinted() {
        URI uri = PriceProbeRuntimeConfig.validateSandboxBaseUrl(
                "https://vinted.pl.example.test"
        );

        assertTrue(
                uri.toString().equals("https://vinted.pl.example.test")
        );
    }
}

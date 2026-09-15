package pl.flipbot.playwright.worker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CatalogConcurrencyConfigTest {

    @Test
    public void defaultsToThreeConcurrentCatalogScans() {
        CatalogConcurrencyConfig config =
                CatalogConcurrencyConfig.fromRaw(null);

        assertEquals(3, config.maxConcurrentCatalogScans());
        assertEquals(1_000L, config.retryDelayMillis());
    }

    @Test
    public void acceptsConfiguredCatalogConcurrency() {
        CatalogConcurrencyConfig config =
                CatalogConcurrencyConfig.fromRaw("7");

        assertEquals(7, config.maxConcurrentCatalogScans());
    }

    @Test
    public void invalidCatalogConcurrencyFallsBackToDefault() {
        assertEquals(
                3,
                CatalogConcurrencyConfig.fromRaw("0")
                        .maxConcurrentCatalogScans()
        );
        assertEquals(
                3,
                CatalogConcurrencyConfig.fromRaw("101")
                        .maxConcurrentCatalogScans()
        );
        assertEquals(
                3,
                CatalogConcurrencyConfig.fromRaw("abc")
                        .maxConcurrentCatalogScans()
        );
    }
}

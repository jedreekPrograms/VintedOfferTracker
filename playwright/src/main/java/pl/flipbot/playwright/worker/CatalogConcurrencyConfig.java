package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.browser.BrowserCapacityController;

@Slf4j
record CatalogConcurrencyConfig(
        int maxConcurrentCatalogScans,
        long retryDelayMillis
) {

    static final String MAX_CONCURRENT_CATALOG_SCANS_ENV =
            "FLIPBOT_MAX_CONCURRENT_CATALOG_SCANS";

    static final int DEFAULT_MAX_CONCURRENT_CATALOG_SCANS = 3;
    static final long DEFAULT_RETRY_DELAY_MILLIS = 1_000L;

    CatalogConcurrencyConfig {
        if (maxConcurrentCatalogScans < 1) {
            throw new IllegalArgumentException(
                    "maxConcurrentCatalogScans must be at least 1"
            );
        }
        if (retryDelayMillis < 1L) {
            throw new IllegalArgumentException(
                    "retryDelayMillis must be at least 1"
            );
        }
    }

    static CatalogConcurrencyConfig fromEnvironment() {
        String configured = System.getenv(MAX_CONCURRENT_CATALOG_SCANS_ENV);
        BrowserCapacityController capacity = BrowserCapacityController.shared();
        if ((configured == null || configured.isBlank()) && capacity.adaptiveEnabled()) {
            // The scheduler ceiling allows growth; the shared browser admission
            // controller still starts conservatively and checks real OS headroom.
            return new CatalogConcurrencyConfig(capacity.maximumConcurrency(), DEFAULT_RETRY_DELAY_MILLIS);
        }
        return fromRaw(configured);
    }

    static CatalogConcurrencyConfig fromRaw(String rawMaxConcurrentScans) {
        int maxConcurrentScans = DEFAULT_MAX_CONCURRENT_CATALOG_SCANS;

        if (rawMaxConcurrentScans != null
                && !rawMaxConcurrentScans.isBlank()) {
            try {
                int parsed = Integer.parseInt(
                        rawMaxConcurrentScans.trim()
                );
                if (parsed < 1 || parsed > 100) {
                    throw new IllegalArgumentException(
                            "Value is outside allowed range 1..100"
                    );
                }
                maxConcurrentScans = parsed;
            } catch (RuntimeException exception) {
                log.warn(
                        "Invalid {}='{}'. Using default {}.",
                        MAX_CONCURRENT_CATALOG_SCANS_ENV,
                        rawMaxConcurrentScans,
                        DEFAULT_MAX_CONCURRENT_CATALOG_SCANS
                );
            }
        }

        return new CatalogConcurrencyConfig(
                maxConcurrentScans,
                DEFAULT_RETRY_DELAY_MILLIS
        );
    }
}

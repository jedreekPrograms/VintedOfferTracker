package pl.flipbot.playwright.marketplace;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarketplaceUrlsTest {

    @Test
    public void acceptsTrustedVintedUrls() {
        assertTrue(MarketplaceUrls.isVintedUrl("https://www.vinted.pl/"));
        assertTrue(MarketplaceUrls.isVintedUrl("https://vinted.pl/catalog?page=1"));
        assertTrue(MarketplaceUrls.isVintedUrl("https://api.vinted.pl/example"));
    }

    @Test
    public void rejectsExternalAndLookalikeHosts() {
        assertFalse(MarketplaceUrls.isVintedUrl("https://www.sos-accessoire.com/example"));
        assertFalse(MarketplaceUrls.isVintedUrl("https://www.vinted.pl.evil.example/catalog"));
        assertFalse(MarketplaceUrls.isVintedUrl("https://evil.example/?next=https://www.vinted.pl/catalog"));
        assertFalse(MarketplaceUrls.isVintedUrl("chrome-error://chromewebdata/"));
        assertFalse(MarketplaceUrls.isVintedUrl("about:blank"));
        assertFalse(MarketplaceUrls.isVintedUrl(null));
    }

    @Test
    public void recognizesOnlyVintedCatalogPathsAsCatalog() {
        assertTrue(MarketplaceUrls.isCatalogUrl("https://www.vinted.pl/catalog"));
        assertTrue(MarketplaceUrls.isCatalogUrl("https://www.vinted.pl/catalog?page=1"));
        assertTrue(MarketplaceUrls.isCatalogUrl("https://www.vinted.pl/catalog/phones?page=1"));

        assertFalse(MarketplaceUrls.isCatalogUrl("https://www.vinted.pl/inbox"));
        assertFalse(MarketplaceUrls.isCatalogUrl("https://www.sos-accessoire.com/catalog"));
        assertFalse(MarketplaceUrls.isCatalogUrl("https://www.vinted.pl.evil.example/catalog"));
    }
}

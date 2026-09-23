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
    public void rejectsExternalLookalikeAndNonHttpsUrls() {
        assertFalse(MarketplaceUrls.isVintedUrl("https://www.sos-accessoire.com/example"));
        assertFalse(MarketplaceUrls.isVintedUrl("https://www.vinted.pl.evil.example/catalog"));
        assertFalse(MarketplaceUrls.isVintedUrl("https://evil.example/?next=https://www.vinted.pl/catalog"));
        assertFalse(MarketplaceUrls.isVintedUrl("http://www.vinted.pl/catalog"));
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
        assertFalse(MarketplaceUrls.isCatalogUrl("https://www.vinted.pl/session-refresh?ref_url=%2Fcatalog"));
        assertFalse(MarketplaceUrls.isCatalogUrl("https://www.sos-accessoire.com/catalog"));
        assertFalse(MarketplaceUrls.isCatalogUrl("https://www.vinted.pl.evil.example/catalog"));
    }

    @Test
    public void recognizesOnlyTrustedVintedSessionRefreshUrls() {
        assertTrue(MarketplaceUrls.isSessionRefreshUrl(
                "https://www.vinted.pl/session-refresh?ref_url=%2Fcatalog"
        ));
        assertTrue(MarketplaceUrls.isSessionRefreshUrl(
                "https://vinted.pl/session-refresh?ref_url=%2Finbox%2F24576946040"
        ));
        assertTrue(MarketplaceUrls.isSessionRefreshUrl(
                "https://www.vinted.pl/session-refresh/"
        ));

        assertFalse(MarketplaceUrls.isSessionRefreshUrl(
                "https://www.vinted.pl/catalog?ref_url=%2Fsession-refresh"
        ));
        assertFalse(MarketplaceUrls.isSessionRefreshUrl(
                "https://www.vinted.pl.evil.example/session-refresh?ref_url=%2Fcatalog"
        ));
        assertFalse(MarketplaceUrls.isSessionRefreshUrl(
                "http://www.vinted.pl/session-refresh?ref_url=%2Fcatalog"
        ));
        assertFalse(MarketplaceUrls.isSessionRefreshUrl(null));
    }
}

package pl.flipbot.playwright.marketplace;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
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
        assertFalse(MarketplaceUrls.isCatalogUrl("https://www.sos-accessoire.com/catalog"));
        assertFalse(MarketplaceUrls.isCatalogUrl("https://www.vinted.pl.evil.example/catalog"));
    }

    @Test
    public void resolvesOnlyTheExpectedVintedListing() {
        assertEquals(
                "https://www.vinted.pl/items/9784253019-samsung-galaxy-s25",
                MarketplaceUrls.resolveVintedListingUrl(
                        "/items/9784253019-samsung-galaxy-s25",
                        "9784253019"
                )
        );
        assertEquals(
                "https://www.vinted.pl/items/9784253019?referrer=catalog",
                MarketplaceUrls.resolveVintedListingUrl(
                        "https://www.vinted.pl/items/9784253019?referrer=catalog",
                        "9784253019"
                )
        );

        assertTrue(MarketplaceUrls.isVintedListingUrl(
                "https://www.vinted.pl/items/9784253019-samsung-galaxy-s25",
                "9784253019"
        ));
    }

    @Test
    public void rejectsWrongListingIdExternalHostsAndUnsafeSchemes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedListingUrl(
                        "https://www.vinted.pl/items/111-other-item",
                        "9784253019"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedListingUrl(
                        "https://evil.example/items/9784253019",
                        "9784253019"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedListingUrl(
                        "https://www.vinted.pl.evil.example/items/9784253019",
                        "9784253019"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedListingUrl(
                        "http://www.vinted.pl/items/9784253019",
                        "9784253019"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedListingUrl(
                        "//evil.example/items/9784253019",
                        "9784253019"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedListingUrl(
                        "/items/97842530190-lookalike-prefix",
                        "9784253019"
                )
        );
    }

    @Test
    public void resolvesOnlyTheExpectedVintedConversation() {
        assertEquals(
                "https://www.vinted.pl/inbox/123456789",
                MarketplaceUrls.resolveVintedConversationUrl(
                        "/inbox/123456789",
                        "123456789"
                )
        );
        assertEquals(
                "https://www.vinted.pl/inbox/123456789?referrer=item",
                MarketplaceUrls.resolveVintedConversationUrl(
                        "https://www.vinted.pl/inbox/123456789?referrer=item",
                        "123456789"
                )
        );
        assertTrue(MarketplaceUrls.isVintedConversationUrl(
                "https://www.vinted.pl/inbox/123456789?referrer=item",
                "123456789"
        ));
    }

    @Test
    public void rejectsWrongOrExternalConversationUrls() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedConversationUrl(
                        "https://www.vinted.pl/inbox/999",
                        "123456789"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedConversationUrl(
                        "https://evil.example/inbox/123456789",
                        "123456789"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedConversationUrl(
                        "http://www.vinted.pl/inbox/123456789",
                        "123456789"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedConversationUrl(
                        "//evil.example/inbox/123456789",
                        "123456789"
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedConversationUrl(
                        "/inbox/123456789",
                        "../123456789"
                )
        );
    }
}

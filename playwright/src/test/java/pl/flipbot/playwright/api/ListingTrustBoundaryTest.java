package pl.flipbot.playwright.api;

import org.junit.Test;
import pl.flipbot.playwright.marketplace.MarketplaceUrls;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ListingTrustBoundaryTest {

    @Test
    public void acceptsScannerStyleRelativeVintedItemAndConversationUrls() {
        assertEquals(
                "https://www.vinted.pl/items/9784253019-samsung-galaxy-s25",
                MarketplaceUrls.resolveVintedListingUrl(
                        "/items/9784253019-samsung-galaxy-s25",
                        "9784253019"
                )
        );

        assertEquals(
                "https://www.vinted.pl/inbox/123456789",
                MarketplaceUrls.resolveVintedConversationUrl(
                        "/inbox/123456789",
                        "123456789"
                )
        );
    }

    @Test
    public void rejectsCrossListingAndCrossConversationReferences() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedListingUrl(
                        "/items/9784253018-other-item",
                        "9784253019"
                )
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> MarketplaceUrls.resolveVintedConversationUrl(
                        "/inbox/123456788",
                        "123456789"
                )
        );
    }
}

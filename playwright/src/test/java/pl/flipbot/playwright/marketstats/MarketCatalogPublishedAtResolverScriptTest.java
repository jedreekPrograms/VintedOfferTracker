package pl.flipbot.playwright.marketstats;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarketCatalogPublishedAtResolverScriptTest {

    @Test
    public void catalogTimestampLookupReadsHydrationWithoutNetworkRequests() {
        String script = MarketCatalogPublishedAtResolver
                .extractCatalogTimestampsScript();

        assertTrue(script.contains("created_at_ts"));
        assertTrue(script.contains("document.documentElement"));
        assertTrue(script.contains("item_id"));
        assertFalse(script.contains("fetch("));
        assertFalse(script.contains("XMLHttpRequest"));
    }
}

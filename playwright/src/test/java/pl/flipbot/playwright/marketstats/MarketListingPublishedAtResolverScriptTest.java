package pl.flipbot.playwright.marketstats;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarketListingPublishedAtResolverScriptTest {

    @Test
    public void hydratedTimestampLookupUsesStringScanningInsteadOfBrokenRegex() {
        String script = MarketListingPublishedAtResolver.extractPublishedAtScript();

        assertTrue(script.contains("created_at_ts"));
        assertTrue(script.contains("indexOf(timestampKey)"));
        assertFalse(script.contains("timestampPattern"));
    }
}

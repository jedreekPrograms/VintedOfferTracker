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

    @Test
    public void publicationRequestsArePacedSequentiallyAndStopOnBackoffSignals() {
        String script = MarketListingPublishedAtResolver.extractPublishedAtScript();

        assertTrue(script.contains("requestSpacingMs = 1000"));
        assertTrue(script.contains("resolveSequentially"));
        assertFalse(script.contains("Promise.all"));
        assertTrue(script.contains("response.status === 429"));
        assertTrue(script.contains("response.status === 403"));
        assertTrue(script.contains("__FLIPBOT_TRAFFIC_BACKOFF__"));
    }
}

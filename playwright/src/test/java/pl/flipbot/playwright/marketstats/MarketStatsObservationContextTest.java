package pl.flipbot.playwright.marketstats;

import org.junit.After;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarketStatsObservationContextTest {

    @After
    public void clearContext() {
        MarketStatsObservationContext.clear(null);
    }

    @Test
    public void newListingsRequirePublicationResolution() {
        MarketStatsObservationContext.begin(
                25L,
                List.of("known"),
                List.of()
        );

        assertFalse(MarketStatsObservationContext.claimPublicationResolution(
                "known"
        ));
        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "new-listing"
        ));
        assertFalse(MarketStatsObservationContext.publicationResolutionCompleteFor(
                25L,
                List.of("known", "new-listing")
        ));

        MarketStatsObservationContext.recordPublishedAt(
                "new-listing",
                LocalDateTime.of(2026, 9, 11, 18, 30)
        );

        assertTrue(MarketStatsObservationContext.publicationResolutionCompleteFor(
                25L,
                List.of("known", "new-listing")
        ));
    }

    @Test
    public void knownListingMissingPublicationTimeIsRetried() {
        MarketStatsObservationContext.begin(
                25L,
                List.of("known-complete", "known-missing"),
                List.of("known-missing")
        );

        assertFalse(MarketStatsObservationContext.claimPublicationResolution(
                "known-complete"
        ));
        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "known-missing"
        ));
    }
}

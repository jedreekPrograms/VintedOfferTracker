package pl.flipbot.playwright.marketstats;

import org.junit.After;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarketStatsObservationContextTest {

    @After
    public void clearContext() {
        MarketStatsObservationContext.clear(null);
    }

    @Test
    public void baselineResolvesPublicationTimeForEveryVisibleListing() {
        MarketStatsObservationContext.begin(
                25L,
                List.of("known-before-baseline"),
                false,
                List.of()
        );

        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "known-before-baseline"
        ));
        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "new-baseline-listing"
        ));
        assertFalse(MarketStatsObservationContext.claimPublicationResolution(
                "new-baseline-listing"
        ));
    }

    @Test
    public void completedBaselineResolvesOnlyNewOrMissingPublicationTimes() {
        MarketStatsObservationContext.begin(
                25L,
                List.of("known-complete", "known-missing"),
                true,
                List.of("known-missing")
        );

        assertFalse(MarketStatsObservationContext.claimPublicationResolution(
                "known-complete"
        ));
        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "known-missing"
        ));
        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "brand-new"
        ));
    }
}

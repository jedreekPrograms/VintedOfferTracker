package pl.flipbot.playwright.marketstats;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketStatsObservationContextTest {

    @AfterEach
    void clearContext() {
        MarketStatsObservationContext.clear(null);
    }

    @Test
    void baselineResolvesPublicationTimeForEveryVisibleListing() {
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
    void completedBaselineResolvesOnlyNewOrMissingPublicationTimes() {
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

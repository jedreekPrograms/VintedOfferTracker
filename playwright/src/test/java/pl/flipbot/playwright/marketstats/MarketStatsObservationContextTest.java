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
                List.of(),
                false
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
                List.of("known-missing"),
                false
        );

        assertFalse(MarketStatsObservationContext.claimPublicationResolution(
                "known-complete"
        ));
        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "known-missing"
        ));
    }

    @Test
    public void fullRetryRefreshesPublicationTimeForEveryVisibleListing() {
        MarketStatsObservationContext.begin(
                25L,
                List.of("known-complete", "known-other"),
                List.of(),
                true
        );

        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "known-complete"
        ));
        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "known-other"
        ));
        assertFalse(MarketStatsObservationContext.publicationResolutionCompleteFor(
                25L,
                List.of("known-complete", "known-other")
        ));

        MarketStatsObservationContext.recordPublishedAt(
                "known-complete",
                LocalDateTime.of(2026, 9, 11, 12, 0)
        );
        MarketStatsObservationContext.recordPublishedAt(
                "known-other",
                LocalDateTime.of(2026, 9, 10, 12, 0)
        );

        assertTrue(MarketStatsObservationContext.publicationResolutionCompleteFor(
                25L,
                List.of("known-complete", "known-other")
        ));
    }
}

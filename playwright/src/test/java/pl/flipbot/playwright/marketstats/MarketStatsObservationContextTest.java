package pl.flipbot.playwright.marketstats;

import org.junit.After;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarketStatsObservationContextTest {

    @After
    public void clearContext() {
        MarketStatsObservationContext.clear(null);
    }

    @Test
    public void newListingsRequirePublicationResolution() {
        LocalDateTime knownPublishedAt =
                LocalDateTime.of(2026, 9, 10, 18, 0);

        MarketStatsObservationContext.begin(
                25L,
                List.of("known"),
                List.of(),
                false,
                false,
                Map.of("known", knownPublishedAt)
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
                false,
                true,
                Map.of(
                        "known-complete",
                        LocalDateTime.of(2026, 9, 10, 12, 0)
                )
        );

        assertFalse(MarketStatsObservationContext.claimPublicationResolution(
                "known-complete"
        ));
        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "known-missing"
        ));
        assertTrue(MarketStatsObservationContext.fullCatalogScanRequired(25L));
    }

    @Test
    public void persistedPublicationTimeFeedsHistoricalBoundaryWithoutBeingRewritten() {
        LocalDateTime persisted =
                LocalDateTime.of(2026, 7, 1, 12, 0);
        LocalDateTime fresh =
                LocalDateTime.of(2026, 7, 2, 12, 0);

        MarketStatsObservationContext.begin(
                25L,
                List.of("known-complete"),
                List.of(),
                false,
                true,
                Map.of("known-complete", persisted)
        );
        MarketStatsObservationContext.recordPublishedAt(
                "new-listing",
                fresh
        );

        Map<String, LocalDateTime> available =
                MarketStatsObservationContext.resolvedFor(
                        25L,
                        List.of("known-complete", "new-listing")
                );
        Map<String, LocalDateTime> freshOnly =
                MarketStatsObservationContext.freshlyResolvedFor(
                        25L,
                        List.of("known-complete", "new-listing")
                );

        assertEquals(persisted, available.get("known-complete"));
        assertEquals(fresh, available.get("new-listing"));
        assertFalse(freshOnly.containsKey("known-complete"));
        assertEquals(fresh, freshOnly.get("new-listing"));
    }

    @Test
    public void fullRetryCanStillForceRefreshOfPersistedPublicationTimes() {
        MarketStatsObservationContext.begin(
                25L,
                List.of("known-complete", "known-other"),
                List.of(),
                true,
                true,
                Map.of(
                        "known-complete",
                        LocalDateTime.of(2026, 9, 11, 12, 0),
                        "known-other",
                        LocalDateTime.of(2026, 9, 10, 12, 0)
                )
        );

        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "known-complete"
        ));
        assertTrue(MarketStatsObservationContext.claimPublicationResolution(
                "known-other"
        ));
    }
}

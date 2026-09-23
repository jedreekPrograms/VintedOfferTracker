package pl.flipbot.playwright.marketstats;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MarketStatsCollectorNavigationTest {

    @Test
    public void catalogPaginationWaitsOnlyForDomContentLoaded() {
        Page.NavigateOptions options =
                MarketStatsCollector.catalogPageNavigateOptions();

        assertEquals(
                WaitUntilState.DOMCONTENTLOADED,
                options.waitUntil
        );
        assertEquals(
                Double.valueOf(30_000),
                options.timeout
        );
    }

    @Test
    public void statisticsWindowStartsAtPreviousWeeksMonday() {
        assertEquals(
                LocalDateTime.of(2026, 8, 31, 0, 0),
                MarketStatsCollector.earliestRelevantPublicationAt(
                        LocalDate.of(2026, 9, 12)
                )
        );
    }

    @Test
    public void twentyConsecutiveOldTailListingsConfirmHistoricalBoundary() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 31, 0, 0);
        List<String> ids = new ArrayList<>();
        Map<String, LocalDateTime> publishedAt = new LinkedHashMap<>();

        for (int index = 0; index < 5; index++) {
            String id = "recent-" + index;
            ids.add(id);
            publishedAt.put(id, cutoff.plusDays(1));
        }

        for (int index = 0; index < 20; index++) {
            String id = "old-" + index;
            ids.add(id);
            publishedAt.put(id, cutoff.minusDays(1));
        }

        assertTrue(
                MarketStatsCollector.hasHistoricalPublicationBoundary(
                        ids,
                        publishedAt,
                        cutoff,
                        20
                )
        );
    }

    @Test
    public void nineteenOldTailListingsDoNotConfirmHistoricalBoundary() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 31, 0, 0);
        List<String> ids = new ArrayList<>();
        Map<String, LocalDateTime> publishedAt = new LinkedHashMap<>();

        String relevantId = "previous-week-start";
        ids.add(relevantId);
        publishedAt.put(relevantId, cutoff);

        for (int index = 0; index < 19; index++) {
            String id = "old-" + index;
            ids.add(id);
            publishedAt.put(id, cutoff.minusDays(1));
        }

        assertFalse(
                MarketStatsCollector.hasHistoricalPublicationBoundary(
                        ids,
                        publishedAt,
                        cutoff,
                        20
                )
        );
    }

    @Test
    public void missingTailPublicationTimeDoesNotConfirmHistoricalBoundary() {
        LocalDateTime cutoff = LocalDateTime.of(2026, 8, 31, 0, 0);
        List<String> ids = new ArrayList<>();
        Map<String, LocalDateTime> publishedAt = new LinkedHashMap<>();

        for (int index = 0; index < 20; index++) {
            String id = "old-" + index;
            ids.add(id);

            if (index < 19) {
                publishedAt.put(id, cutoff.minusDays(1));
            }
        }

        assertFalse(
                MarketStatsCollector.hasHistoricalPublicationBoundary(
                        ids,
                        publishedAt,
                        cutoff,
                        20
                )
        );
    }
}

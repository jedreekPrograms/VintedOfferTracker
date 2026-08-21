package pl.flipbot.playwright.processing;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class CatalogCandidateProcessorTest {

    @Test
    public void oldestBacklogStartsTheCycleBeforeGenuinelyNewListings() {
        List<ListingResponseDto> discovered = List.of(
                listing(1L, "B1"),
                listing(2L, "N3"),
                listing(3L, "B2"),
                listing(4L, "N1"),
                listing(5L, "N4"),
                listing(6L, "B3"),
                listing(7L, "N2")
        );

        Set<String> currentScan = new LinkedHashSet<>(
                List.of("N1", "N2", "N3", "N4")
        );
        Set<String> newlyClaimed = Set.of("N1", "N2", "N3", "N4");

        CatalogCandidateProcessor.CandidateSelection selection =
                CatalogCandidateProcessor.selectCandidates(
                        discovered,
                        currentScan,
                        newlyClaimed
                );

        assertEquals(
                List.of("N1", "N2", "N3", "N4"),
                ids(selection.fresh())
        );
        assertEquals(
                List.of("B1", "B2", "B3"),
                ids(selection.backlog())
        );
        assertEquals(
                List.of("B1", "N1", "N2", "B2", "N3", "N4", "B3"),
                ids(selection.prioritized())
        );
    }

    @Test
    public void yesterdayLeftoverIsInsideTheFirstRealOfferBatchWhenThreeNewItemsArrive() {
        List<ListingResponseDto> discovered = List.of(
                listing(1L, "YESTERDAY_LEFTOVER"),
                listing(2L, "TODAY_1"),
                listing(3L, "TODAY_2"),
                listing(4L, "TODAY_3")
        );

        Set<String> currentScan = new LinkedHashSet<>(
                List.of("TODAY_1", "TODAY_2", "TODAY_3")
        );
        Set<String> newlyClaimed = Set.of(
                "TODAY_1",
                "TODAY_2",
                "TODAY_3"
        );

        CatalogCandidateProcessor.CandidateSelection selection =
                CatalogCandidateProcessor.selectCandidates(
                        discovered,
                        currentScan,
                        newlyClaimed
                );

        assertEquals(
                List.of("YESTERDAY_LEFTOVER", "TODAY_1", "TODAY_2"),
                ids(selection.prioritized()).subList(0, 3)
        );
    }

    @Test
    public void previouslyDiscoveredItemStillVisibleOnPageOneRemainsBacklog() {
        List<ListingResponseDto> discovered = List.of(
                listing(1L, "OLD_VISIBLE"),
                listing(2L, "NEW_1"),
                listing(3L, "NEW_2")
        );

        Set<String> currentScan = new LinkedHashSet<>(
                List.of("NEW_1", "NEW_2", "OLD_VISIBLE")
        );
        Set<String> newlyClaimed = Set.of("NEW_1", "NEW_2");

        CatalogCandidateProcessor.CandidateSelection selection =
                CatalogCandidateProcessor.selectCandidates(
                        discovered,
                        currentScan,
                        newlyClaimed
                );

        assertEquals(List.of("NEW_1", "NEW_2"), ids(selection.fresh()));
        assertEquals(List.of("OLD_VISIBLE"), ids(selection.backlog()));
        assertEquals(
                List.of("OLD_VISIBLE", "NEW_1", "NEW_2"),
                ids(selection.prioritized())
        );
    }

    @Test
    public void backlogIsStillProcessedWhenCurrentScanIsEmpty() {
        List<ListingResponseDto> discovered = List.of(
                listing(1L, "B1"),
                listing(2L, "B2")
        );

        CatalogCandidateProcessor.CandidateSelection selection =
                CatalogCandidateProcessor.selectCandidates(
                        discovered,
                        Set.of(),
                        Set.of()
                );

        assertEquals(List.of(), ids(selection.fresh()));
        assertEquals(List.of("B1", "B2"), ids(selection.backlog()));
        assertEquals(List.of("B1", "B2"), ids(selection.prioritized()));
    }

    @Test
    public void allGenuinelyNewListingsKeepCurrentNewestFirstOrderWhenThereIsNoBacklog() {
        List<ListingResponseDto> discovered = List.of(
                listing(1L, "N3"),
                listing(2L, "N1"),
                listing(3L, "N2")
        );

        Set<String> currentScan = new LinkedHashSet<>(
                List.of("N1", "N2", "N3")
        );
        Set<String> newlyClaimed = Set.of("N1", "N2", "N3");

        CatalogCandidateProcessor.CandidateSelection selection =
                CatalogCandidateProcessor.selectCandidates(
                        discovered,
                        currentScan,
                        newlyClaimed
                );

        assertEquals(List.of("N1", "N2", "N3"), ids(selection.fresh()));
        assertEquals(List.of(), ids(selection.backlog()));
        assertEquals(List.of("N1", "N2", "N3"), ids(selection.prioritized()));
    }

    private List<String> ids(List<ListingResponseDto> listings) {
        return listings.stream()
                .map(ListingResponseDto::listingId)
                .toList();
    }

    private ListingResponseDto listing(Long id, String marketplaceId) {
        return new ListingResponseDto(
                id,
                marketplaceId,
                "Samsung Galaxy S25",
                "https://www.vinted.pl/items/" + marketplaceId + "-samsung-galaxy-s25",
                new BigDecimal("1500.00"),
                new BigDecimal("1500.00"),
                0,
                false,
                null,
                null,
                "DISCOVERED",
                null
        );
    }
}

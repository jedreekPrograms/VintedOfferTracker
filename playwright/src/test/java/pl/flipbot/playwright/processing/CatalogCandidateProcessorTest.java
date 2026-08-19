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
    public void currentScanKeepsPriorityWhileBacklogAlwaysMakesProgress() {
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

        CatalogCandidateProcessor.CandidateSelection selection =
                CatalogCandidateProcessor.selectCandidates(
                        discovered,
                        currentScan
                );

        assertEquals(
                List.of("N1", "N2", "N3", "N4"),
                ids(selection.currentScan())
        );
        assertEquals(
                List.of("B1", "B2", "B3"),
                ids(selection.backlog())
        );
        assertEquals(
                List.of("N1", "N2", "N3", "B1", "B2", "N4", "B3"),
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
                        Set.of()
                );

        assertEquals(List.of(), ids(selection.currentScan()));
        assertEquals(List.of("B1", "B2"), ids(selection.backlog()));
        assertEquals(List.of("B1", "B2"), ids(selection.prioritized()));
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

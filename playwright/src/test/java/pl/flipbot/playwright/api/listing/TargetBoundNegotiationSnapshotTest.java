package pl.flipbot.playwright.api.listing;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class TargetBoundNegotiationSnapshotTest {
    @Test public void immutableJobSnapshotIsIsolatedByBotAndProductIncludingInactiveTargets() {
        var primary = listing(1L, null);
        var extra = listing(2L, 77L);
        var inactive = listing(3L, 99L);
        var rows = new ArrayList<>(List.of(primary, extra, inactive));
        var mainClient = new TargetBoundListingClient(null, 7L, rows);
        var extraClient = new TargetBoundListingClient(77L, 7L, rows);
        var inactiveClient = new TargetBoundListingClient(99L, 7L, rows);
        rows.clear();
        assertEquals(List.of(primary), mainClient.getNegotiatingListings(7L));
        assertEquals(List.of(extra), extraClient.getNegotiatingListings(7L));
        assertEquals(List.of(inactive), inactiveClient.getNegotiatingListings(7L));
        assertThrows(IllegalArgumentException.class, () -> mainClient.getNegotiatingListings(8L));
        assertThrows(UnsupportedOperationException.class, () -> extraClient.getNegotiatingListings(7L).clear());
        // A following job sees a new backend result, not the old snapshot.
        assertTrue(new TargetBoundListingClient(77L, 7L, List.of()).getNegotiatingListings(7L).isEmpty());
    }

    private ListingResponseDto listing(Long id, Long target) {
        return new ListingResponseDto(id, id.toString(), "Offer", "url", null, null, 0, true,
                "conversation", "conversation-url", "NEGOTIATING", null,
                null, null, null, null, null, target, "Product");
    }
}

package pl.flipbot.playwright.marketstats;

import org.junit.Test;
import pl.flipbot.playwright.marketstats.dto.MarketObservationBatchResponseDto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MarketStatsApiClientResponseTest {

    @Test
    public void completionPostDoesNotEraseDiscoveryCount() {
        MarketObservationBatchResponseDto discovery =
                new MarketObservationBatchResponseDto(
                        36L,
                        false,
                        96,
                        7,
                        "2026-09-26T21:00:00",
                        false
                );

        MarketObservationBatchResponseDto completion =
                new MarketObservationBatchResponseDto(
                        36L,
                        false,
                        96,
                        0,
                        "2026-09-26T21:00:03",
                        true
                );

        MarketObservationBatchResponseDto merged =
                MarketStatsApiClient.preserveDiscoveryCount(
                        discovery,
                        completion
                );

        assertEquals(7, merged.newListings());
        assertEquals(96, merged.observedListings());
        assertTrue(merged.complete());
        assertEquals(
                "2026-09-26T21:00:03",
                merged.scannedAt()
        );
    }
}

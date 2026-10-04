package pl.flipbot.marketstats;

import org.junit.jupiter.api.Test;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.marketstats.dto.MarketStatsHealthResponse;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketStatsHealthServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");

    @Test
    void healthyRecentCompleteScansAreReportedAsOk() {
        DictionaryModelRepository modelRepository =
                mock(DictionaryModelRepository.class);
        MarketModelScanStateRepository scanStateRepository =
                mock(MarketModelScanStateRepository.class);

        LocalDateTime now = LocalDateTime.now(WARSAW);
        MarketModelScanState state = MarketModelScanState.builder()
                .trackingGeneration(1)
                .initializedAt(now.minusDays(5))
                .baselineCompleteAt(now.minusDays(4))
                .lastScanAt(now.minusMinutes(5))
                .lastSuccessfulScanAt(now.minusMinutes(5))
                .lastScanComplete(true)
                .build();

        when(modelRepository.count()).thenReturn(1L);
        when(scanStateRepository.findAll()).thenReturn(List.of(state));

        MarketStatsHealthResponse response =
                new MarketStatsHealthService(
                        modelRepository,
                        scanStateRepository
                ).getHealth();

        assertEquals(MarketStatsHealthStatus.OK, response.status());
        assertEquals(1, response.baselineReadyModels());
        assertEquals(0, response.incompleteModels());
    }

    @Test
    void oneFreshModelDoesNotHideAnotherStaleModel() {
        DictionaryModelRepository modelRepository =
                mock(DictionaryModelRepository.class);
        MarketModelScanStateRepository scanStateRepository =
                mock(MarketModelScanStateRepository.class);

        LocalDateTime now = LocalDateTime.now(WARSAW);
        MarketModelScanState fresh = MarketModelScanState.builder()
                .trackingGeneration(1)
                .initializedAt(now.minusDays(5))
                .baselineCompleteAt(now.minusDays(4))
                .lastScanAt(now.minusMinutes(5))
                .lastSuccessfulScanAt(now.minusMinutes(5))
                .lastScanComplete(true)
                .build();
        MarketModelScanState stale = MarketModelScanState.builder()
                .trackingGeneration(1)
                .initializedAt(now.minusDays(5))
                .baselineCompleteAt(now.minusDays(4))
                .lastScanAt(now.minusHours(4))
                .lastSuccessfulScanAt(now.minusHours(4))
                .lastScanComplete(true)
                .build();

        when(modelRepository.count()).thenReturn(2L);
        when(scanStateRepository.findAll()).thenReturn(List.of(fresh, stale));

        MarketStatsHealthResponse response =
                new MarketStatsHealthService(
                        modelRepository,
                        scanStateRepository
                ).getHealth();

        assertEquals(MarketStatsHealthStatus.STALE, response.status());
        assertEquals(1, response.staleModels());
    }

    @Test
    void incompleteLatestScanIsNotShownAsHealthy() {
        DictionaryModelRepository modelRepository =
                mock(DictionaryModelRepository.class);
        MarketModelScanStateRepository scanStateRepository =
                mock(MarketModelScanStateRepository.class);

        LocalDateTime now = LocalDateTime.now(WARSAW);
        MarketModelScanState state = MarketModelScanState.builder()
                .trackingGeneration(1)
                .initializedAt(now.minusDays(5))
                .baselineCompleteAt(now.minusDays(4))
                .lastScanAt(now.minusMinutes(2))
                .lastSuccessfulScanAt(now.minusMinutes(20))
                .lastScanComplete(false)
                .build();

        when(modelRepository.count()).thenReturn(1L);
        when(scanStateRepository.findAll()).thenReturn(List.of(state));

        MarketStatsHealthResponse response =
                new MarketStatsHealthService(
                        modelRepository,
                        scanStateRepository
                ).getHealth();

        assertEquals(MarketStatsHealthStatus.PARTIAL, response.status());
        assertEquals(1, response.incompleteModels());
    }
}

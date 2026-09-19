package pl.flipbot.marketstats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketStatsScanCompletenessServiceTest {

    @Mock
    private MarketModelScanStateRepository scanStateRepository;

    private MarketStatsScanCompletenessService service;

    @BeforeEach
    void setUp() {
        service = new MarketStatsScanCompletenessService(scanStateRepository);
    }

    @Test
    void requiresFullScanWhenPreviousScanWasIncomplete() {
        MarketModelScanState state = MarketModelScanState.builder()
                .baselineCompleteAt(LocalDateTime.of(2026, 9, 9, 10, 0))
                .publicationWindowCompleteAt(LocalDateTime.of(2026, 9, 9, 10, 5))
                .lastScanComplete(false)
                .build();

        when(scanStateRepository.findById(68L)).thenReturn(Optional.of(state));

        assertTrue(service.requiresFullCatalogScan(68L));
    }

    @Test
    void allowsKnownBoundaryAfterCompletePublicationWindowScan() {
        MarketModelScanState state = MarketModelScanState.builder()
                .baselineCompleteAt(LocalDateTime.of(2026, 9, 9, 10, 0))
                .publicationWindowCompleteAt(LocalDateTime.of(2026, 9, 9, 10, 5))
                .lastScanComplete(true)
                .build();

        when(scanStateRepository.findById(68L)).thenReturn(Optional.of(state));

        assertFalse(service.requiresFullCatalogScan(68L));
    }

    @Test
    void requiresFullScanForLegacyCompleteStateWithoutPublicationCoverage() {
        MarketModelScanState state = MarketModelScanState.builder()
                .baselineCompleteAt(LocalDateTime.of(2026, 9, 9, 10, 0))
                .publicationWindowCompleteAt(null)
                .lastScanComplete(true)
                .build();

        when(scanStateRepository.findById(68L)).thenReturn(Optional.of(state));

        assertTrue(service.requiresFullCatalogScan(68L));
    }

    @Test
    void requiresFullScanWhenNoStateExistsYet() {
        when(scanStateRepository.findById(68L)).thenReturn(Optional.empty());

        assertTrue(service.requiresFullCatalogScan(68L));
    }
}

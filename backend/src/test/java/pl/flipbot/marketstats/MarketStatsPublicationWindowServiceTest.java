package pl.flipbot.marketstats;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketStatsPublicationWindowServiceTest {

    @Mock
    private MarketModelScanStateRepository scanStateRepository;

    private MarketStatsPublicationWindowService service;

    @BeforeEach
    void setUp() {
        service = new MarketStatsPublicationWindowService(scanStateRepository);
    }

    @Test
    void marksPublicationWindowAfterCompleteBaselineScan() {
        MarketModelScanState state = MarketModelScanState.builder()
                .baselineCompleteAt(LocalDateTime.of(2026, 9, 12, 20, 0))
                .lastScanComplete(true)
                .build();

        when(scanStateRepository.findByModelIdForUpdate(68L))
                .thenReturn(Optional.of(state));

        LocalDateTime completedAt = service.markPublicationWindowComplete(68L);

        assertNotNull(completedAt);
        assertNotNull(state.getPublicationWindowCompleteAt());
        verify(scanStateRepository).save(state);
    }

    @Test
    void refusesToMarkCoverageAfterIncompleteScan() {
        MarketModelScanState state = MarketModelScanState.builder()
                .baselineCompleteAt(LocalDateTime.of(2026, 9, 12, 20, 0))
                .lastScanComplete(false)
                .build();

        when(scanStateRepository.findByModelIdForUpdate(68L))
                .thenReturn(Optional.of(state));

        assertThrows(
                IllegalStateException.class,
                () -> service.markPublicationWindowComplete(68L)
        );
    }
}

package pl.flipbot.marketstats;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.dictionary.DictionaryBrand;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketStatsServiceTest {

    @Test
    void resetStartsNewGenerationWithoutDeletingHistoricalObservations() {
        DictionaryModelRepository modelRepository =
                mock(DictionaryModelRepository.class);
        BotConfigurationRepository configurationRepository =
                mock(BotConfigurationRepository.class);
        BotAdditionalTargetRepository additionalTargetRepository =
                mock(BotAdditionalTargetRepository.class);
        MarketModelScanStateRepository scanStateRepository =
                mock(MarketModelScanStateRepository.class);
        MarketListingObservationRepository observationRepository =
                mock(MarketListingObservationRepository.class);
        RealActionAuditRepository realActionAuditRepository =
                mock(RealActionAuditRepository.class);

        MarketModelScanState state = MarketModelScanState.builder()
                .trackingGeneration(3)
                .initializedAt(LocalDateTime.now().minusDays(10))
                .baselineCompleteAt(LocalDateTime.now().minusDays(9))
                .baselineOfferCount(120)
                .publicationWindowCompleteAt(LocalDateTime.now().minusDays(8))
                .lastScanAt(LocalDateTime.now().minusMinutes(10))
                .lastSuccessfulScanAt(LocalDateTime.now().minusMinutes(10))
                .lastScanComplete(true)
                .build();

        DictionaryModel model = DictionaryModel.builder()
                .id(30L)
                .name("Galaxy S24")
                .brand(DictionaryBrand.builder().id(1L).name("Samsung").build())
                .build();

        when(modelRepository.findById(30L))
                .thenReturn(Optional.of(model));
        when(scanStateRepository.findByModelIdForUpdate(30L))
                .thenReturn(Optional.of(state));

        MarketStatsService service = new MarketStatsService(
                modelRepository,
                configurationRepository,
                additionalTargetRepository,
                scanStateRepository,
                observationRepository,
                realActionAuditRepository
        );

        service.resetModelTracking(30L);

        assertEquals(4, state.getTrackingGeneration());
        assertNull(state.getBaselineCompleteAt());
        assertNull(state.getBaselineOfferCount());
        assertNull(state.getPublicationWindowCompleteAt());
        assertNull(state.getLastSuccessfulScanAt());
        assertFalse(state.getLastScanComplete());

        verify(scanStateRepository).save(state);
        verify(observationRepository, never()).deleteByModel_Id(anyLong());
    }
}

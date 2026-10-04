package pl.flipbot.marketstats;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.dictionary.DictionaryBrand;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.marketstats.dto.MarketObservationBatchRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                observationRepository
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
    @Test
    void incrementalNewListingUsesFirstSeenAsPublicationFallback() {
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

        DictionaryModel model = DictionaryModel.builder()
                .id(30L)
                .name("Galaxy S24")
                .brand(DictionaryBrand.builder().id(1L).name("Samsung").build())
                .build();
        MarketModelScanState state = MarketModelScanState.builder()
                .model(model)
                .trackingGeneration(1)
                .initializedAt(LocalDateTime.now().minusDays(5))
                .baselineCompleteAt(LocalDateTime.now().minusDays(4))
                .publicationWindowCompleteAt(LocalDateTime.now().minusDays(3))
                .lastSuccessfulScanAt(LocalDateTime.now().minusMinutes(15))
                .lastScanComplete(true)
                .build();

        when(modelRepository.findByIdForUpdate(30L))
                .thenReturn(Optional.of(model));
        when(scanStateRepository.findByModelIdForUpdate(30L))
                .thenReturn(Optional.of(state));
        when(observationRepository
                .findAllByModel_IdAndTrackingGenerationAndMarketplaceListingIdIn(
                        30L,
                        1,
                        List.of("new-123")
                ))
                .thenReturn(List.of());

        MarketStatsService service = new MarketStatsService(
                modelRepository,
                configurationRepository,
                additionalTargetRepository,
                scanStateRepository,
                observationRepository
        );

        service.recordObservations(
                30L,
                new MarketObservationBatchRequest(
                        List.of("new-123"),
                        false,
                        null,
                        null,
                        1,
                        Map.of()
                )
        );

        verify(observationRepository).saveAll(
                org.mockito.ArgumentMatchers.argThat(observations -> {
                    MarketListingObservation saved =
                            observations.iterator().next();
                    assertNotNull(saved.getPublishedAt());
                    assertEquals(saved.getFirstSeenAt(), saved.getPublishedAt());
                    assertFalse(Boolean.TRUE.equals(saved.getBaseline()));
                    return true;
                })
        );
    }

    @Test
    void staleObserverBatchIsRejectedAfterTrackingGenerationChanges() {
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

        DictionaryModel model = DictionaryModel.builder()
                .id(30L)
                .name("Galaxy S24")
                .brand(DictionaryBrand.builder().id(1L).name("Samsung").build())
                .build();
        MarketModelScanState state = MarketModelScanState.builder()
                .model(model)
                .trackingGeneration(4)
                .initializedAt(LocalDateTime.now())
                .lastScanComplete(false)
                .build();

        when(modelRepository.findByIdForUpdate(30L))
                .thenReturn(Optional.of(model));
        when(scanStateRepository.findByModelIdForUpdate(30L))
                .thenReturn(Optional.of(state));

        MarketStatsService service = new MarketStatsService(
                modelRepository,
                configurationRepository,
                additionalTargetRepository,
                scanStateRepository,
                observationRepository
        );

        MarketObservationBatchRequest staleRequest =
                new MarketObservationBatchRequest(
                        List.of("123"),
                        true,
                        null,
                        null,
                        3,
                        Map.of()
                );

        assertThrows(
                IllegalStateException.class,
                () -> service.recordObservations(30L, staleRequest)
        );
    }

}

package pl.flipbot.marketstats;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotAdditionalTargetRepository;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.BotConfigurationRepository;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.dictionary.DictionaryBrand;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.analytics.HistoryModelResolver;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.marketstats.dto.CalendarModelPlanningResponse;
import pl.flipbot.negotiation.audit.RealActionAudit;
import pl.flipbot.negotiation.audit.RealActionAuditRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

class MarketStatsCalendarPlanningServiceTest {

    @Test
    void freshnessWindowScalesWithLargeObserverTargetSets() {
        assertEquals(
                120L,
                MarketStatsCalendarPlanningService
                        .currentWindowFreshnessMinutes(30)
        );
        assertEquals(
                200L,
                MarketStatsCalendarPlanningService
                        .currentWindowFreshnessMinutes(100)
        );
        assertEquals(
                360L,
                MarketStatsCalendarPlanningService
                        .currentWindowFreshnessMinutes(500)
        );
    }

    @Test
    void activeAdditionalTargetCountsAsExistingBotAndUsesObservedCapacity() {
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
        ListingRepository listingRepository =
                mock(ListingRepository.class);
        HistoryModelResolver historyModelResolver =
                mock(HistoryModelResolver.class);

        DictionaryBrand samsung = DictionaryBrand.builder()
                .id(1L)
                .name("Samsung")
                .build();
        DictionaryModel s26 = DictionaryModel.builder()
                .id(30L)
                .brand(samsung)
                .name("Galaxy S26")
                .targetMode(TargetMode.VINTED_MODEL)
                .build();

        Bot bot = Bot.builder()
                .id(9L)
                .name("S26 multi")
                .marketStatsObserver(false)
                .build();
        BotConfiguration configuration = BotConfiguration.builder()
                .id(90L)
                .bot(bot)
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("Galaxy S24")
                .build();
        BotAdditionalTarget additionalTarget = BotAdditionalTarget.builder()
                .id(901L)
                .configuration(configuration)
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("Galaxy S26")
                .active(true)
                .build();

        List<RealActionAudit> audits = new ArrayList<>();
        // Align the fixture with production's Europe/Warsaw calendar. The old
        // JVM-local clock fixture started near 23:56 UTC on CI, and adding
        // minutes split the 8 confirmed actions across two calendar days.
        // That reduced the observed daily capacity below 8 intermittently.
        // Noon on yesterday's Warsaw date leaves all 8 actions on one day.
        LocalDateTime yesterday = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .minusDays(1)
                .atTime(12, 0);
        for (int index = 0; index < 8; index++) {
            audits.add(
                    RealActionAudit.builder()
                            .botId(9L)
                            .backendListingId(100L)
                            .createdAt(yesterday.plusMinutes(index))
                            .build()
            );
        }

        Listing listing = Listing.builder()
                .id(100L)
                .productTargetLabel("Samsung → Galaxy S26")
                .build();

        when(modelRepository.findAll()).thenReturn(List.of(s26));
        when(configurationRepository.findAll()).thenReturn(List.of(configuration));
        when(additionalTargetRepository.findAll()).thenReturn(List.of(additionalTarget));
        when(scanStateRepository.findAllById(List.of(30L))).thenReturn(List.of());
        when(realActionAuditRepository
                .findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                        any(),
                        any(),
                        any(LocalDateTime.class)
                ))
                .thenReturn(audits);
        when(listingRepository.findAllById(any()))
                .thenReturn(List.of(listing));
        when(historyModelResolver.resolveModelId(listing, List.of(s26)))
                .thenReturn(Optional.of(30L));

        MarketStatsCalendarPlanningService service =
                new MarketStatsCalendarPlanningService(
                        modelRepository,
                        configurationRepository,
                        additionalTargetRepository,
                        scanStateRepository,
                        observationRepository,
                        realActionAuditRepository,
                        listingRepository,
                        historyModelResolver
                );

        CalendarModelPlanningResponse response =
                service.getPlanning().getFirst();

        assertEquals(1, response.existingBots());
        assertEquals(8, response.dailyConversationCapacityPerBot());
        assertEquals(56, response.weeklyConversationCapacityPerBot());
        // 8 confirmed audits belong to one listing; resolve the history model once.
        verify(historyModelResolver, times(1)).resolveModelId(listing, List.of(s26));
        verify(scanStateRepository).findAllById(List.of(30L));
        verify(scanStateRepository, never()).findById(30L);
    }

    @Test
    void fetchesStatesOnceAndMapsThemToCorrectModelEvenWhenRowsAreOutOfOrder() {
        DictionaryModelRepository models = mock(DictionaryModelRepository.class);
        BotConfigurationRepository configs = mock(BotConfigurationRepository.class);
        BotAdditionalTargetRepository targets = mock(BotAdditionalTargetRepository.class);
        MarketModelScanStateRepository scanStates = mock(MarketModelScanStateRepository.class);
        MarketListingObservationRepository observations = mock(MarketListingObservationRepository.class);
        RealActionAuditRepository audits = mock(RealActionAuditRepository.class);
        ListingRepository listings = mock(ListingRepository.class);
        HistoryModelResolver history = mock(HistoryModelResolver.class);

        DictionaryBrand samsung = DictionaryBrand.builder().id(1L).name("Samsung").build();
        DictionaryModel s25 = DictionaryModel.builder()
                .id(25L).brand(samsung).name("Galaxy S25").build();
        DictionaryModel s26 = DictionaryModel.builder()
                .id(26L).brand(samsung).name("Galaxy S26").build();
        LocalDateTime successful = LocalDate.of(2026, 10, 8).atTime(12, 0);
        MarketModelScanState state26 = MarketModelScanState.builder()
                .modelId(26L).lastSuccessfulScanAt(successful).lastScanComplete(true).build();

        when(models.findAll()).thenReturn(List.of(s25, s26));
        when(configs.findAll()).thenReturn(List.of());
        when(targets.findAll()).thenReturn(List.of());
        when(scanStates.findAllById(List.of(25L, 26L))).thenReturn(List.of(state26));
        when(audits.findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                any(), any(), any(LocalDateTime.class))).thenReturn(List.of());

        MarketStatsCalendarPlanningService service = new MarketStatsCalendarPlanningService(
                models, configs, targets, scanStates, observations, audits, listings, history
        );
        List<CalendarModelPlanningResponse> responses = service.getPlanning();

        assertEquals(2, responses.size());
        assertEquals(25L, responses.get(0).modelId());
        assertEquals(null, responses.get(0).lastSuccessfulScanAt());
        assertEquals(26L, responses.get(1).modelId());
        assertEquals(successful, responses.get(1).lastSuccessfulScanAt());
        verify(scanStates).findAllById(List.of(25L, 26L));
        verify(scanStates, never()).findById(25L);
        verify(scanStates, never()).findById(26L);
    }
    @Test
    void oneGroupedCountQueryReplacesPerModelPublicationCounts() {
        DictionaryModelRepository models = mock(DictionaryModelRepository.class);
        BotConfigurationRepository configs = mock(BotConfigurationRepository.class);
        BotAdditionalTargetRepository targets = mock(BotAdditionalTargetRepository.class);
        MarketModelScanStateRepository states = mock(MarketModelScanStateRepository.class);
        MarketListingObservationRepository observations = mock(MarketListingObservationRepository.class);
        RealActionAuditRepository audits = mock(RealActionAuditRepository.class);
        ListingRepository listings = mock(ListingRepository.class);
        HistoryModelResolver history = mock(HistoryModelResolver.class);

        DictionaryBrand brand = DictionaryBrand.builder().id(5L).name("Samsung").build();
        DictionaryModel complete = DictionaryModel.builder()
                .id(100L).brand(brand).name("Galaxy S25").build();
        DictionaryModel recent = DictionaryModel.builder()
                .id(200L).brand(brand).name("Galaxy S26").build();
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Warsaw"));
        MarketModelScanState oldState = MarketModelScanState.builder()
                .modelId(100L)
                .baselineCompleteAt(today.minusDays(40).atStartOfDay())
                .publicationWindowCompleteAt(today.minusDays(39).atStartOfDay())
                .lastSuccessfulScanAt(today.atTime(12, 0))
                .lastScanComplete(true).trackingGeneration(2).build();
        MarketModelScanState newState = MarketModelScanState.builder()
                .modelId(200L)
                .baselineCompleteAt(today.minusDays(1).atStartOfDay())
                .lastSuccessfulScanAt(today.atTime(12, 0))
                .lastScanComplete(false).trackingGeneration(1).build();

        when(models.findAll()).thenReturn(List.of(complete, recent));
        when(configs.findAll()).thenReturn(List.of());
        when(targets.findAll()).thenReturn(List.of());
        when(states.findAllById(List.of(100L, 200L)))
                .thenReturn(List.of(newState, oldState));
        // Deliberately reversed SQL row order: association must use model ID.
        when(observations.countPublishedListingWindows(
                org.mockito.ArgumentMatchers.eq(List.of(100L, 200L)),
                any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(LocalDateTime.class)
        )).thenReturn(List.of(
                new Object[]{200L, 2L, 3L, 4L, 5L},
                new Object[]{100L, 7L, 15L, 21L, 45L}
        ));
        when(audits.findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                any(), any(), any(LocalDateTime.class))).thenReturn(List.of());

        MarketStatsCalendarPlanningService service = new MarketStatsCalendarPlanningService(
                models, configs, targets, states, observations, audits, listings, history
        );
        List<CalendarModelPlanningResponse> result = service.getPlanning();

        assertEquals(2, result.size());
        CalendarModelPlanningResponse old = result.get(0);
        CalendarModelPlanningResponse latest = result.get(1);
        assertEquals(100L, old.modelId());
        assertEquals(7, old.offersToday());
        assertEquals(15, old.offersCurrentWeek());
        assertEquals(21, old.offersPreviousFullWeek());
        assertEquals(21, old.recommendationWeeklyOffers());
        assertEquals(false, old.recommendationEstimated());

        assertEquals(200L, latest.modelId());
        assertEquals(2, latest.offersToday());
        assertEquals(3, latest.offersCurrentWeek());
        assertEquals(null, latest.offersPreviousFullWeek());
        assertEquals(18, latest.recommendationWeeklyOffers());
        assertEquals(true, latest.recommendationEstimated());

        verify(observations, org.mockito.Mockito.times(1)).countPublishedListingWindows(
                org.mockito.ArgumentMatchers.eq(List.of(100L, 200L)),
                any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(LocalDateTime.class)
        );
        verify(observations, never()).countPublishedListingsBetween(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyInt(),
                any(LocalDateTime.class), any(LocalDateTime.class)
        );
    }

    @Test
    void noModelsSkipsGroupedCountQueryEntirely() {
        DictionaryModelRepository models = mock(DictionaryModelRepository.class);
        BotConfigurationRepository configs = mock(BotConfigurationRepository.class);
        BotAdditionalTargetRepository targets = mock(BotAdditionalTargetRepository.class);
        MarketModelScanStateRepository states = mock(MarketModelScanStateRepository.class);
        MarketListingObservationRepository observations = mock(MarketListingObservationRepository.class);
        RealActionAuditRepository audits = mock(RealActionAuditRepository.class);
        ListingRepository listings = mock(ListingRepository.class);
        HistoryModelResolver history = mock(HistoryModelResolver.class);
        when(models.findAll()).thenReturn(List.of());
        when(configs.findAll()).thenReturn(List.of());
        when(targets.findAll()).thenReturn(List.of());
        when(states.findAllById(List.of())).thenReturn(List.of());
        when(audits.findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                any(), any(), any(LocalDateTime.class))).thenReturn(List.of());

        MarketStatsCalendarPlanningService service = new MarketStatsCalendarPlanningService(
                models, configs, targets, states, observations, audits, listings, history
        );
        assertEquals(List.of(), service.getPlanning());
        verify(observations, never()).countPublishedListingWindows(
                org.mockito.ArgumentMatchers.anyCollection(),
                any(LocalDateTime.class), any(LocalDateTime.class),
                any(LocalDateTime.class), any(LocalDateTime.class)
        );
    }

    @Test
    void unknownHistoricalModelIsResolvedOnlyOnceEvenForRepeatedAuditRows() {
        DictionaryModelRepository models = mock(DictionaryModelRepository.class);
        BotConfigurationRepository configs = mock(BotConfigurationRepository.class);
        BotAdditionalTargetRepository targets = mock(BotAdditionalTargetRepository.class);
        MarketModelScanStateRepository states = mock(MarketModelScanStateRepository.class);
        MarketListingObservationRepository observations = mock(MarketListingObservationRepository.class);
        RealActionAuditRepository audits = mock(RealActionAuditRepository.class);
        ListingRepository listings = mock(ListingRepository.class);
        HistoryModelResolver history = mock(HistoryModelResolver.class);

        DictionaryBrand samsung = DictionaryBrand.builder().id(1L).name("Samsung").build();
        DictionaryModel s26 = DictionaryModel.builder()
                .id(26L).brand(samsung).name("Galaxy S26").build();
        LocalDateTime yesterday = LocalDate.now(ZoneId.of("Europe/Warsaw"))
                .minusDays(1).atTime(12, 0);
        Listing unknown = Listing.builder().id(701L).build();
        RealActionAudit first = RealActionAudit.builder()
                .botId(3L).backendListingId(701L).createdAt(yesterday).build();
        RealActionAudit second = RealActionAudit.builder()
                .botId(3L).backendListingId(701L).createdAt(yesterday.plusMinutes(5)).build();

        when(models.findAll()).thenReturn(List.of(s26));
        when(configs.findAll()).thenReturn(List.of());
        when(targets.findAll()).thenReturn(List.of());
        when(states.findAllById(List.of(26L))).thenReturn(List.of());
        when(audits.findAllByActionTypeAndOutcomeAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                any(), any(), any(LocalDateTime.class))).thenReturn(List.of(first, second));
        when(listings.findAllById(any())).thenReturn(List.of(unknown));
        when(history.resolveModelId(unknown, List.of(s26))).thenReturn(Optional.empty());

        MarketStatsCalendarPlanningService service = new MarketStatsCalendarPlanningService(
                models, configs, targets, states, observations, audits, listings, history
        );
        assertEquals(5, service.getPlanning().getFirst().dailyConversationCapacityPerBot());
        verify(history, times(1)).resolveModelId(unknown, List.of(s26));
    }

}

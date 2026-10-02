package pl.flipbot.analytics;

import org.junit.jupiter.api.Test;
import pl.flipbot.dashboard.DashboardPeriod;
import pl.flipbot.dictionary.DictionaryBrand;
import pl.flipbot.dictionary.DictionaryModel;
import pl.flipbot.dictionary.DictionaryModelRepository;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.marketstats.MarketListingObservation;
import pl.flipbot.marketstats.MarketListingObservationRepository;
import pl.flipbot.marketstats.MarketModelScanState;
import pl.flipbot.marketstats.MarketModelScanStateRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    @Test
    void dailyTimelineCalculatesAverageMedianAndKeepsMissingDayAsEmptyBucket() {
        AnalyticsService service = serviceWithObservations(
                observation("1", "2026-10-01T10:00:00", "100"),
                observation("2", "2026-10-01T12:00:00", "300"),
                observation("3", "2026-10-03T09:00:00", "500")
        );

        var overview = service.getOverview(
                DashboardPeriod.ALL,
                Set.of(1L),
                Set.of(),
                Set.of(),
                AnalyticsSource.OBSERVER,
                AnalyticsGranularity.DAY,
                LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-03")
        );

        assertEquals(3, overview.timeline().size());

        var first = overview.timeline().get(0);
        assertEquals(LocalDate.parse("2026-10-01"), first.date());
        assertEquals(2L, first.marketListingCount());
        assertEquals(2, first.marketPriceSampleCount());
        assertEquals(new BigDecimal("200.00"), first.averageMarketPrice());
        assertEquals(new BigDecimal("200.00"), first.medianMarketPrice());

        var missing = overview.timeline().get(1);
        assertEquals(LocalDate.parse("2026-10-02"), missing.date());
        assertEquals(0L, missing.marketListingCount());
        assertEquals(0, missing.marketPriceSampleCount());
        assertNull(missing.averageMarketPrice());
        assertNull(missing.medianMarketPrice());

        var third = overview.timeline().get(2);
        assertEquals(LocalDate.parse("2026-10-03"), third.date());
        assertEquals(new BigDecimal("500.00"), third.averageMarketPrice());
        assertEquals(new BigDecimal("500.00"), third.medianMarketPrice());
    }

    @Test
    void weeklyTimelineUsesMondayBucketsAndCalculatesEachWeekSeparately() {
        AnalyticsService service = serviceWithObservations(
                observation("1", "2026-10-01T10:00:00", "100"),
                observation("2", "2026-10-03T12:00:00", "300"),
                observation("3", "2026-10-08T09:00:00", "700")
        );

        var overview = service.getOverview(
                DashboardPeriod.ALL,
                Set.of(1L),
                Set.of(),
                Set.of(),
                AnalyticsSource.OBSERVER,
                AnalyticsGranularity.WEEK,
                LocalDate.parse("2026-09-28"),
                LocalDate.parse("2026-10-11")
        );

        assertEquals(2, overview.timeline().size());

        var firstWeek = overview.timeline().get(0);
        assertEquals(LocalDate.parse("2026-09-28"), firstWeek.date());
        assertEquals("Tydz. 28.09.2026", firstWeek.label());
        assertEquals(2L, firstWeek.marketListingCount());
        assertEquals(new BigDecimal("200.00"), firstWeek.averageMarketPrice());
        assertEquals(new BigDecimal("200.00"), firstWeek.medianMarketPrice());

        var secondWeek = overview.timeline().get(1);
        assertEquals(LocalDate.parse("2026-10-05"), secondWeek.date());
        assertEquals(1L, secondWeek.marketListingCount());
        assertEquals(new BigDecimal("700.00"), secondWeek.averageMarketPrice());
        assertEquals(new BigDecimal("700.00"), secondWeek.medianMarketPrice());
    }

    @Test
    void monthlyTimelineCalculatesAverageAndMedianPerCalendarMonth() {
        AnalyticsService service = serviceWithObservations(
                observation("1", "2026-09-10T10:00:00", "100"),
                observation("2", "2026-09-20T12:00:00", "300"),
                observation("3", "2026-10-03T09:00:00", "500"),
                observation("4", "2026-10-08T09:00:00", "900")
        );

        var overview = service.getOverview(
                DashboardPeriod.ALL,
                Set.of(1L),
                Set.of(),
                Set.of(),
                AnalyticsSource.OBSERVER,
                AnalyticsGranularity.MONTH,
                LocalDate.parse("2026-09-01"),
                LocalDate.parse("2026-10-31")
        );

        assertEquals(2, overview.timeline().size());

        var september = overview.timeline().get(0);
        assertEquals(LocalDate.parse("2026-09-01"), september.date());
        assertEquals("09.2026", september.label());
        assertEquals(2L, september.marketListingCount());
        assertEquals(new BigDecimal("200.00"), september.averageMarketPrice());
        assertEquals(new BigDecimal("200.00"), september.medianMarketPrice());

        var october = overview.timeline().get(1);
        assertEquals(LocalDate.parse("2026-10-01"), october.date());
        assertEquals("10.2026", october.label());
        assertEquals(2L, october.marketListingCount());
        assertEquals(new BigDecimal("700.00"), october.averageMarketPrice());
        assertEquals(new BigDecimal("700.00"), october.medianMarketPrice());
    }

    private AnalyticsService serviceWithObservations(
            MarketListingObservation... observations
    ) {
        ListingRepository listingRepository = mock(ListingRepository.class);
        MarketListingObservationRepository observationRepository =
                mock(MarketListingObservationRepository.class);
        MarketModelScanStateRepository scanStateRepository =
                mock(MarketModelScanStateRepository.class);
        DictionaryModelRepository modelRepository =
                mock(DictionaryModelRepository.class);
        HistoryModelResolver historyModelResolver =
                mock(HistoryModelResolver.class);

        DictionaryModel model = model();
        MarketModelScanState state = MarketModelScanState.builder()
                .modelId(1L)
                .model(model)
                .trackingGeneration(1)
                .initializedAt(LocalDateTime.parse("2026-08-01T00:00:00"))
                .baselineCompleteAt(LocalDateTime.parse("2026-08-01T00:00:00"))
                .lastScanAt(LocalDateTime.parse("2026-10-10T00:00:00"))
                .lastSuccessfulScanAt(LocalDateTime.parse("2026-10-10T00:00:00"))
                .lastScanComplete(true)
                .build();

        for (MarketListingObservation observation : observations) {
            observation.setModel(model);
            observation.setTrackingGeneration(1);
        }

        when(modelRepository.findAll()).thenReturn(List.of(model));
        when(scanStateRepository.findAll()).thenReturn(List.of(state));
        when(observationRepository.findAll()).thenReturn(List.of(observations));

        return new AnalyticsService(
                listingRepository,
                observationRepository,
                scanStateRepository,
                modelRepository,
                historyModelResolver
        );
    }

    private MarketListingObservation observation(
            String marketplaceListingId,
            String publishedAt,
            String price
    ) {
        LocalDateTime published = LocalDateTime.parse(publishedAt);
        BigDecimal value = new BigDecimal(price);

        return MarketListingObservation.builder()
                .marketplaceListingId(marketplaceListingId)
                .firstSeenAt(published.plusMinutes(1))
                .lastSeenAt(published.plusHours(1))
                .publishedAt(published)
                .firstSeenPrice(value)
                .latestPrice(value)
                .lowestSeenPrice(value)
                .highestSeenPrice(value)
                .baseline(false)
                .build();
    }

    private DictionaryModel model() {
        return DictionaryModel.builder()
                .id(1L)
                .name("Galaxy S26")
                .brand(
                        DictionaryBrand.builder()
                                .id(1L)
                                .name("Samsung")
                                .build()
                )
                .build();
    }
}

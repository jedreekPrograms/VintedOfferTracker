package pl.flipbot.marketstats;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.dictionary.DictionaryBrand;
import pl.flipbot.dictionary.DictionaryModel;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Runs against the CI PostgreSQL service, not an H2 or mocked SQL parser.
 * Every row is rolled back so persistent user/observer data are untouched.
 */
@SpringBootTest
@Transactional
class MarketListingObservationWindowsSqlTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MarketListingObservationRepository observationRepository;

    @Test
    void groupedWindowsPreserveExactDatesActiveGenerationAndPerModelBaseline() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 7, 12, 0);
        LocalDateTime todayStart = LocalDateTime.of(2026, 10, 7, 0, 0);
        LocalDateTime thisWeekStart = LocalDateTime.of(2026, 10, 5, 0, 0);
        LocalDateTime previousWeekStart = LocalDateTime.of(2026, 9, 28, 0, 0);

        DictionaryBrand brand = DictionaryBrand.builder().name("Calendar SQL regression").build();
        entityManager.persist(brand);

        DictionaryModel main = model(brand, "SQL Model A");
        DictionaryModel other = model(brand, "SQL Model B");
        entityManager.persist(main);
        entityManager.persist(other);
        entityManager.flush();

        // Two tracked generations: old-generation observations must not leak
        // into a newly started observer scan.
        state(main, 2, LocalDateTime.of(2026, 9, 27, 0, 0), now);
        state(other, 1, LocalDateTime.of(2026, 10, 6, 0, 0), now);
        entityManager.flush();

        observe(main, 2, "a-baseline", LocalDateTime.of(2026, 9, 27, 0, 0));
        observe(main, 2, "a-prev-boundary", previousWeekStart);
        observe(main, 2, "a-current-boundary", thisWeekStart);
        observe(main, 2, "a-today-boundary", todayStart);
        observe(main, 2, "a-today", LocalDateTime.of(2026, 10, 7, 11, 0));
        observe(main, 2, "a-exact-now-excluded", now);
        observe(main, 1, "a-old-generation-excluded", LocalDateTime.of(2026, 10, 7, 10, 0));
        observe(main, 2, "a-unknown-publication-excluded", null);

        observe(other, 1, "b-before-baseline", thisWeekStart);
        observe(other, 1, "b-after-baseline", LocalDateTime.of(2026, 10, 6, 8, 0));
        observe(other, 1, "b-today", LocalDateTime.of(2026, 10, 7, 9, 0));
        entityManager.flush();

        List<Object[]> rows = observationRepository.countPublishedListingWindows(
                List.of(main.getId(), other.getId()),
                todayStart, thisWeekStart, previousWeekStart, now
        );
        Map<Long, long[]> byModel = new HashMap<>();
        for (Object[] row : rows) {
            byModel.put(((Number) row[0]).longValue(), new long[]{
                    ((Number) row[1]).longValue(),
                    ((Number) row[2]).longValue(),
                    ((Number) row[3]).longValue(),
                    ((Number) row[4]).longValue()
            });
        }

        assertEquals(2, rows.size());
        // Main: today=2, current week=3, last full week=1, since baseline=5.
        assertArrayEquals(new long[]{2, 3, 1, 5}, byModel.get(main.getId()));
        // Other: a current-week observation predates its own baseline.
        assertArrayEquals(new long[]{1, 3, 0, 2}, byModel.get(other.getId()));
    }

    private DictionaryModel model(DictionaryBrand brand, String name) {
        return DictionaryModel.builder().brand(brand).name(name)
                .targetMode(TargetMode.VINTED_MODEL).build();
    }

    private void state(
            DictionaryModel model, int generation, LocalDateTime baseline, LocalDateTime now
    ) {
        entityManager.persist(MarketModelScanState.builder()
                .model(model)
                .trackingGeneration(generation)
                .initializedAt(baseline.minusDays(1))
                .baselineCompleteAt(baseline)
                .lastScanAt(now)
                .lastScanComplete(true)
                .build());
    }

    private void observe(
            DictionaryModel model, int generation, String listingId, LocalDateTime publishedAt
    ) {
        LocalDateTime firstSeen = LocalDateTime.of(2026, 9, 20, 10, 0);
        entityManager.persist(MarketListingObservation.builder()
                .model(model)
                .trackingGeneration(generation)
                .marketplaceListingId(listingId)
                .firstSeenAt(firstSeen)
                .lastSeenAt(firstSeen)
                .publishedAt(publishedAt)
                .baseline(false)
                .build());
    }
}

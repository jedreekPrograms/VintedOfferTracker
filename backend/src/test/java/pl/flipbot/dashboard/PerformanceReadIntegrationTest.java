package pl.flipbot.dashboard;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.scheduler.SchedulerRunningBotService;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingHistoryService;
import pl.flipbot.listing.ListingStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class PerformanceReadIntegrationTest {
    @Autowired EntityManager em;
    @Autowired BotRepository bots;
    @Autowired DashboardStatsService stats;
    @Autowired ListingHistoryService history;
    @Autowired RuntimeDashboardService runtime;
    @Autowired SchedulerRunningBotService sync;

    @Test
    void projectionsPreservePeriodsMoneyHiddenHistoryAndObserverIsolationWithoutLoadingEntities() {
        Bot bot = bots.save(Bot.builder().name("Performance test").email("perf-test@example.invalid")
                .status(BotStatus.RUNNING).build());
        Bot observer = bots.save(Bot.builder().name("Observer test").email("perf-observer@example.invalid")
                .status(BotStatus.RUNNING).marketStatsObserver(true).build());
        LocalDateTime today = LocalDate.now().atStartOfDay();
        save(bot, "one", ListingStatus.PURCHASED, "3", "2", today.plusHours(1), false);
        save(bot, "two", ListingStatus.PURCHASED, "7", "4", today.plusHours(2), false);
        save(bot, "older", ListingStatus.PURCHASED, "10", "20", today.minusDays(2), false);
        save(bot, "undated", ListingStatus.PURCHASED, "100", "50", null, false);
        save(bot, "hidden", ListingStatus.PURCHASED, "500", "1", today, true);
        save(bot, "skipped", ListingStatus.SKIPPED_BY_USER, "10", "10", today, false);
        // Current-state counters include hidden entries and ignore the history period.
        save(bot, "negotiating", ListingStatus.NEGOTIATING, "10", "10", null, true);
        save(bot, "action", ListingStatus.ACTION_REQUIRED, "10", "10", null, false);
        for (int i = 0; i < 20; i++) save(bot, "backlog-" + i, ListingStatus.DISCOVERED,
                "10", "10", null, false);
        em.flush();
        Long botId = bot.getId();
        Long observerId = observer.getId();
        em.clear();

        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        boolean previous = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            var daily = stats.getStats(DashboardPeriod.TODAY);
            assertEquals(1L, daily.activeBotsCount());
            assertEquals(1L, daily.negotiatingCount());
            assertEquals(1L, daily.actionRequiredCount());
            assertEquals(2L, daily.purchasedCount());
            assertEquals(1L, daily.skippedByUserCount());
            money("6", daily.totalSpent());
            money("4", daily.totalNegotiatedSavings());
            money("3", daily.averagePurchasePrice());
            money("38.10", daily.averageDiscountPercentage());

            var all = stats.getStats(DashboardPeriod.ALL);
            assertEquals(4L, all.purchasedCount());
            money("76", all.totalSpent());
            money("54", all.totalNegotiatedSavings());
            money("19", all.averagePurchasePrice());
            money("31.55", all.averageDiscountPercentage());

            var rows = history.getHistory();
            assertEquals(5, rows.size());
            assertEquals("two", rows.getFirst().getListingId());
            assertEquals("undated", rows.getLast().getListingId());
            assertTrue(rows.stream().allMatch(row -> row.getBotId().equals(botId)));
            assertTrue(rows.stream().allMatch(row -> row.getAdditionalTargetId() == null));
            assertTrue(rows.stream().noneMatch(row -> row.getListingId().equals("hidden")));

            assertEquals(java.util.List.of(botId), bots.findIdsByStatus(BotStatus.RUNNING));
            assertTrue(bots.findRuntimeBots().stream().noneMatch(row -> row.getId().equals(observerId)));
            assertEquals(1, runtime.getRuntimeDashboard().totalBots());
            assertTrue(sync.getRunningBots().getFirst().isHasActiveNegotiations());
            assertEquals(0, statistics.getEntityLoadCount(), "Read projections must not hydrate Bot/config/listing graphs");
        } finally { statistics.setStatisticsEnabled(previous); }
    }

    private void save(Bot bot, String id, ListingStatus status, String original, String price,
                      LocalDateTime date, boolean hidden) {
        em.persist(Listing.builder().bot(bot).listingId(id).title(id).url("https://example.invalid/" + id)
                .originalPrice(new BigDecimal(original)).currentPrice(new BigDecimal(price))
                .currentStep(0).awaitingSellerResponse(false).status(status).decisionAt(date)
                .historyHidden(hidden).build());
    }

    private void money(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}

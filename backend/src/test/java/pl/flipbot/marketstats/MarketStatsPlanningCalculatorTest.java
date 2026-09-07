package pl.flipbot.marketstats;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketStatsPlanningCalculatorTest {

    @Test
    void calendarWindowsStartAtMidnightAndMonday() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 6, 20, 21);

        MarketStatsPlanningCalculator.CalendarWindows windows =
                MarketStatsPlanningCalculator.windows(now);

        assertEquals(
                LocalDateTime.of(2026, 9, 6, 0, 0),
                windows.todayStart()
        );
        assertEquals(
                LocalDateTime.of(2026, 8, 31, 0, 0),
                windows.currentWeekStart()
        );
        assertEquals(
                LocalDateTime.of(2026, 8, 24, 0, 0),
                windows.previousWeekStart()
        );
    }

    @Test
    void mondayMidnightStartsANewCurrentWeek() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 7, 0, 0);

        MarketStatsPlanningCalculator.CalendarWindows windows =
                MarketStatsPlanningCalculator.windows(now);

        assertEquals(now, windows.todayStart());
        assertEquals(now, windows.currentWeekStart());
        assertEquals(
                LocalDateTime.of(2026, 8, 31, 0, 0),
                windows.previousWeekStart()
        );
    }

    @Test
    void fallbackProjectsAvailableDailyAverageToSevenDays() {
        assertEquals(
                14,
                MarketStatsPlanningCalculator.projectWeeklyOffers(6, 3)
        );
        assertEquals(
                7,
                MarketStatsPlanningCalculator.projectWeeklyOffers(1, 1)
        );
        assertEquals(
                0,
                MarketStatsPlanningCalculator.projectWeeklyOffers(0, 4)
        );
    }

    @Test
    void queueGrowthIncreasesRequiredBotPool() {
        assertEquals(
                48,
                MarketStatsPlanningCalculator.effectiveWeeklyDemandFromQueueTrend(
                        20,
                        36,
                        32
                )
        );
        assertEquals(
                6,
                MarketStatsPlanningCalculator.recommendedBotsFromQueueTrend(
                        20,
                        36,
                        32,
                        4
                )
        );
    }

    @Test
    void queueShrinkageCanShowExcessBots() {
        assertEquals(
                12,
                MarketStatsPlanningCalculator.effectiveWeeklyDemandFromQueueTrend(
                        40,
                        20,
                        32
                )
        );
        assertEquals(
                2,
                MarketStatsPlanningCalculator.recommendedBotsFromQueueTrend(
                        40,
                        20,
                        32,
                        4
                )
        );
    }

    @Test
    void stableQueueKeepsObservedPoolAsRequirement() {
        assertEquals(
                32,
                MarketStatsPlanningCalculator.effectiveWeeklyDemandFromQueueTrend(
                        20,
                        20,
                        32
                )
        );
        assertEquals(
                4,
                MarketStatsPlanningCalculator.recommendedBotsFromQueueTrend(
                        20,
                        20,
                        32,
                        4
                )
        );
    }

    @Test
    void recommendationDoesNotInventCapacityWithoutRealStarts() {
        assertNull(
                MarketStatsPlanningCalculator.recommendedBotsFromQueueTrend(
                        20,
                        30,
                        0,
                        4
                )
        );
        assertNull(
                MarketStatsPlanningCalculator.recommendedBotsFromQueueTrend(
                        20,
                        30,
                        12,
                        0
                )
        );
        assertEquals(
                0,
                MarketStatsPlanningCalculator.recommendedBotsFromQueueTrend(
                        20,
                        0,
                        0,
                        4
                )
        );
    }

    @Test
    void observedConversationsPerBotUsesTheRealCompletedWeek() {
        assertEquals(
                4.5,
                MarketStatsPlanningCalculator.observedConversationsPerBot(18, 4),
                0.0001
        );
        assertEquals(
                0.0,
                MarketStatsPlanningCalculator.observedConversationsPerBot(0, 4),
                0.0001
        );
        assertNull(
                MarketStatsPlanningCalculator.observedConversationsPerBot(18, 0)
        );
    }

    @Test
    void windowIsCompleteOnlyWhenTrackingStartedNoLaterThanItsStart() {
        LocalDateTime monday = LocalDateTime.of(2026, 8, 31, 0, 0);

        assertTrue(
                MarketStatsPlanningCalculator.coversWindowFrom(
                        monday.minusMinutes(1),
                        monday
                )
        );
        assertTrue(
                MarketStatsPlanningCalculator.coversWindowFrom(
                        monday,
                        monday
                )
        );
        assertFalse(
                MarketStatsPlanningCalculator.coversWindowFrom(
                        monday.plusSeconds(1),
                        monday
                )
        );
    }
}

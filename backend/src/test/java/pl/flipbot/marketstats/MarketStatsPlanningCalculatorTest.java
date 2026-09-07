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
    void recommendationScalesFromActuallyObservedThroughput() {
        assertEquals(
                16,
                MarketStatsPlanningCalculator.recommendedBotsFromObservedThroughput(
                        72,
                        18,
                        4
                )
        );
        assertEquals(
                8,
                MarketStatsPlanningCalculator.recommendedBotsFromObservedThroughput(
                        72,
                        36,
                        4
                )
        );
        assertEquals(
                4,
                MarketStatsPlanningCalculator.recommendedBotsFromObservedThroughput(
                        72,
                        72,
                        4
                )
        );
        assertEquals(
                0,
                MarketStatsPlanningCalculator.recommendedBotsFromObservedThroughput(
                        0,
                        0,
                        4
                )
        );
    }

    @Test
    void recommendationDoesNotInventCapacityWithoutRealStarts() {
        assertNull(
                MarketStatsPlanningCalculator.recommendedBotsFromObservedThroughput(
                        72,
                        0,
                        4
                )
        );
        assertNull(
                MarketStatsPlanningCalculator.recommendedBotsFromObservedThroughput(
                        72,
                        12,
                        0
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

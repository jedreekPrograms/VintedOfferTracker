package pl.flipbot.marketstats;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void recommendationUsesThirtyFiveNewConversationsPerBotPerWeek() {
        assertEquals(0, MarketStatsPlanningCalculator.recommendedBots(0));
        assertEquals(1, MarketStatsPlanningCalculator.recommendedBots(35));
        assertEquals(2, MarketStatsPlanningCalculator.recommendedBots(36));
        assertEquals(3, MarketStatsPlanningCalculator.recommendedBots(71));
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

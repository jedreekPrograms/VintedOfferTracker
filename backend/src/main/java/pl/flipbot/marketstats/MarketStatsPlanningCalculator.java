package pl.flipbot.marketstats;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

final class MarketStatsPlanningCalculator {

    static final int DAYS_PER_WEEK = 7;
    static final int NEW_CONVERSATIONS_PER_BOT_PER_DAY = 5;

    private MarketStatsPlanningCalculator() {
    }

    static CalendarWindows windows(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate currentMonday = today.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
        LocalDate previousMonday = currentMonday.minusWeeks(1);

        return new CalendarWindows(
                today.atStartOfDay(),
                currentMonday.atStartOfDay(),
                previousMonday.atStartOfDay(),
                now
        );
    }

    static boolean coversWindowFrom(
            LocalDateTime trackingStartedAt,
            LocalDateTime windowStart
    ) {
        return trackingStartedAt != null
                && !trackingStartedAt.isAfter(windowStart);
    }

    static int trackedCalendarDays(
            LocalDateTime trackingStartedAt,
            LocalDateTime now
    ) {
        if (trackingStartedAt == null || now == null || trackingStartedAt.isAfter(now)) {
            return 0;
        }

        long days = ChronoUnit.DAYS.between(
                trackingStartedAt.toLocalDate(),
                now.toLocalDate()
        ) + 1L;

        return safeInt(Math.max(days, 1L));
    }

    static int projectWeeklyOffers(
            long observedOffers,
            int trackedCalendarDays
    ) {
        if (observedOffers <= 0L || trackedCalendarDays <= 0) {
            return 0;
        }

        long numerator = observedOffers * DAYS_PER_WEEK;
        long projected = (numerator + trackedCalendarDays - 1L)
                / trackedCalendarDays;

        return safeInt(projected);
    }

    static int recommendedBots(int weeklyOffers) {
        if (weeklyOffers <= 0) {
            return 0;
        }

        int weeklyCapacityPerBot =
                DAYS_PER_WEEK * NEW_CONVERSATIONS_PER_BOT_PER_DAY;

        return (weeklyOffers + weeklyCapacityPerBot - 1)
                / weeklyCapacityPerBot;
    }

    private static int safeInt(long value) {
        return value > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) value;
    }

    record CalendarWindows(
            LocalDateTime todayStart,
            LocalDateTime currentWeekStart,
            LocalDateTime previousWeekStart,
            LocalDateTime now
    ) {
    }
}

package pl.flipbot.marketstats;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

final class MarketStatsPlanningCalculator {

    static final int DAYS_PER_WEEK = 7;

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

    /**
     * Scales the actually observed bot pool to the number of bots that would
     * have been needed to cover the whole completed week's opportunity set at
     * the same realised throughput.
     *
     * Example: 4 bots opened 18 conversations out of 72 opportunities. Their
     * realised throughput was 4.5 new conversations per bot/week, therefore
     * covering all 72 at the same throughput would require 16 bots.
     *
     * No theoretical daily capacity is assumed here. Long-running
     * negotiations, seller response times, retries and operational downtime are
     * already reflected in the number of conversations the pool really opened.
     */
    static Integer recommendedBotsFromObservedThroughput(
            int weeklyOpportunities,
            int conversationsStarted,
            int observedBotCount
    ) {
        if (weeklyOpportunities <= 0) {
            return 0;
        }

        if (conversationsStarted <= 0 || observedBotCount <= 0) {
            return null;
        }

        long numerator = (long) weeklyOpportunities * observedBotCount;
        long required = (numerator + conversationsStarted - 1L)
                / conversationsStarted;

        return safeInt(required);
    }

    static Double observedConversationsPerBot(
            int conversationsStarted,
            int observedBotCount
    ) {
        if (observedBotCount <= 0) {
            return null;
        }

        return Math.max(conversationsStarted, 0)
                / (double) observedBotCount;
    }

    private static int safeInt(long value) {
        return value > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) Math.max(value, 0L);
    }

    record CalendarWindows(
            LocalDateTime todayStart,
            LocalDateTime currentWeekStart,
            LocalDateTime previousWeekStart,
            LocalDateTime now
    ) {
    }
}

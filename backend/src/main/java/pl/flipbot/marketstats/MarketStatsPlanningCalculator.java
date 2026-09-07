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
     * Derives the effective amount of work that arrived during a completed
     * week from what the pool actually processed and how the unstarted queue
     * changed between Monday 00:00 and the following Monday 00:00.
     *
     * handled + (queueEnd - queueStart)
     *
     * Example: 32 conversations were started and the queue grew from 20 to 36.
     * Effective weekly demand was therefore 48 items. If the queue instead fell
     * from 40 to 20, effective demand was 12 items. A negative result is clamped
     * to zero because removals/expiry can make the reconstructed queue fall more
     * than the number of conversations that were started.
     */
    static int effectiveWeeklyDemandFromQueueTrend(
            int queueStart,
            int queueEnd,
            int conversationsStarted
    ) {
        long effectiveDemand = Math.max(conversationsStarted, 0)
                + (long) Math.max(queueEnd, 0)
                - Math.max(queueStart, 0);

        return safeInt(Math.max(effectiveDemand, 0L));
    }

    /**
     * Estimates how many bots would have been needed for the effective demand
     * seen in the completed week, using the pool's realised conversations per
     * bot as the throughput unit. This makes queue growth increase the required
     * pool and queue shrinkage decrease it instead of assuming that every
     * observed listing had to be contacted in the same week.
     */
    static Integer recommendedBotsFromQueueTrend(
            int queueStart,
            int queueEnd,
            int conversationsStarted,
            int observedBotCount
    ) {
        int effectiveDemand = effectiveWeeklyDemandFromQueueTrend(
                queueStart,
                queueEnd,
                conversationsStarted
        );

        if (effectiveDemand == 0) {
            return 0;
        }

        if (conversationsStarted <= 0 || observedBotCount <= 0) {
            return null;
        }

        long numerator = (long) effectiveDemand * observedBotCount;
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

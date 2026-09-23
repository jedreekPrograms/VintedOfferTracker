package pl.flipbot.bot.activity.dto;

import java.time.LocalDate;

public record BotDailyActivityResponse(
        Long botId,
        LocalDate date,
        String timeZone,
        int dailyLimit,
        int dailyLimitUsed,
        int dailyLimitRemaining,
        int activeNegotiations,
        int newNegotiationsToday,
        int nextStepsInNegotiationsStartedToday,
        int nextStepsInOlderNegotiations,
        int confirmedActionsToday,
        int ambiguousActionsToday,
        int usedSlotsWithoutAuditYet
) {
}

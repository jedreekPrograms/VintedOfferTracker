package pl.flipbot.dashboard.dto;

import java.time.Instant;

public record RuntimeDashboardBotResponse(
        Long botId,
        String name,
        String botStatus,
        String runtimeStatus,
        Instant lastRunStartedAt,
        Instant lastRunFinishedAt,
        Instant nextRunAt,
        Long lastRunDurationMs,
        int consecutiveFailures,
        String lastError,
        Integer workerSlot,
        Instant sessionBlockedSince,
        int sessionBlockCount,
        Instant updatedAt
) {
}

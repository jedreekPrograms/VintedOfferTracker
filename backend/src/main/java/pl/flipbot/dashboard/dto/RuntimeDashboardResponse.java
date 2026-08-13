package pl.flipbot.dashboard.dto;

import java.util.List;

public record RuntimeDashboardResponse(
        long totalBots,
        long runningBots,
        long idleCount,
        long queuedCount,
        long workingCount,
        long cooldownCount,
        long errorCount,
        double averageLastRunDurationMs,
        List<RuntimeDashboardBotResponse> bots
) {
}

package pl.flipbot.bot.runtime.dto;

import lombok.Builder;
import lombok.Getter;
import pl.flipbot.bot.runtime.BotRuntimeStatus;

import java.time.Instant;

@Getter
@Builder
public class BotRuntimeStateResponse {

    private Long botId;

    private BotRuntimeStatus runtimeStatus;

    private Instant lastRunStartedAt;

    private Instant lastRunFinishedAt;

    private Instant nextRunAt;

    private Long lastRunDurationMs;

    private int consecutiveFailures;

    private String lastError;

    private Integer workerSlot;

    private Instant sessionBlockedSince;

    private int sessionBlockCount;

    private Instant updatedAt;
}

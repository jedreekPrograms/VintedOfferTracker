package pl.flipbot.bot.runtime.dto;

import lombok.Getter;
import lombok.Setter;
import pl.flipbot.bot.runtime.RuntimeEventType;

@Getter
@Setter
public class BotRuntimeEventRequest {

    private RuntimeEventType eventType;

    private Long nextRunAtEpochMs;

    private Long durationMs;

    private Integer workerSlot;

    private String errorMessage;
}

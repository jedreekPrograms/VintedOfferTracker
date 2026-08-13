package pl.flipbot.bot.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.bot.runtime.dto.BotRuntimeEventRequest;
import pl.flipbot.bot.runtime.dto.BotRuntimeStateResponse;

@RestController
@RequestMapping("/api/bots/{botId}/runtime")
@RequiredArgsConstructor
public class BotRuntimeStateController {

    private final BotRuntimeStateService runtimeStateService;

    @GetMapping
    public BotRuntimeStateResponse getRuntimeState(
            @PathVariable Long botId
    ) {
        return runtimeStateService.getRuntimeState(botId);
    }

    @PatchMapping
    public BotRuntimeStateResponse applyRuntimeEvent(
            @PathVariable Long botId,
            @RequestBody BotRuntimeEventRequest request
    ) {
        return runtimeStateService.applyEvent(botId, request);
    }
}

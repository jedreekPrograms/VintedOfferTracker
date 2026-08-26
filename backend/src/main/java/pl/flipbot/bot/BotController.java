package pl.flipbot.bot;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.bot.dto.BotEditCapabilitiesResponse;
import pl.flipbot.bot.dto.BotPlaywrightResponse;
import pl.flipbot.bot.dto.BotResponse;
import pl.flipbot.bot.dto.CreateBotRequest;
import pl.flipbot.bot.dto.RunningBotResponse;
import pl.flipbot.bot.dto.UpdateBotRequest;

import java.util.List;

@RestController
@RequestMapping("/api/bots")
@RequiredArgsConstructor
public class BotController {

    private final BotService botService;

    private final BotDeletionService botDeletionService;

    @GetMapping
    @Transactional(readOnly = true)
    public List<BotResponse> getAllBots() {
        return botService.getAllBots();
    }

    @GetMapping("/{botId}")
    @Transactional(readOnly = true)
    public BotResponse getBot(
            @PathVariable Long botId
    ) {
        return botService.getBot(botId);
    }

    @GetMapping("/{botId}/edit-capabilities")
    @Transactional(readOnly = true)
    public BotEditCapabilitiesResponse getEditCapabilities(
            @PathVariable Long botId
    ) {
        return botService.getEditCapabilities(botId);
    }

    @PostMapping
    public BotResponse createBot(
            @Valid @RequestBody CreateBotRequest request
    ) {
        return botService.createBot(request);
    }

    @PatchMapping("/{botId}")
    public BotResponse updateBot(
            @PathVariable Long botId,
            @Valid @RequestBody UpdateBotRequest request
    ) {
        return botService.updateBot(botId, request);
    }

    @DeleteMapping("/{botId}")
    public void deleteBot(
            @PathVariable Long botId
    ) {
        botDeletionService.deleteBot(botId);
    }

    @GetMapping("/running")
    public List<RunningBotResponse> getRunningBotIds() {
        return botService.getRunningBotIds();
    }

    @PatchMapping("/{botId}/start")
    public void startBot(
            @PathVariable Long botId
    ) {
        botService.startBot(botId);
    }

    @PatchMapping("/{botId}/stop")
    public void stopBot(
            @PathVariable Long botId
    ) {
        botService.stopBot(botId);
    }

    @GetMapping("/{botId}/playwright")
    @Transactional(readOnly = true)
    public BotPlaywrightResponse getPlaywrightBot(
            @PathVariable Long botId
    ) {
        return botService.getPlaywrightBot(botId);
    }
}

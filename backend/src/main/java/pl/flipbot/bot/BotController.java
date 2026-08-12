package pl.flipbot.bot;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
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
    public List<BotResponse> getAllBots() {

        return botService.getAllBots();
    }

    @GetMapping("/{botId}")
    public BotResponse getBot(
            @PathVariable Long botId
    ) {

        return botService.getBot(
                botId
        );
    }

    @PostMapping
    public BotResponse createBot(
            @Valid @RequestBody CreateBotRequest request
    ) {

        return botService.createBot(
                request
        );
    }

    @PatchMapping("/{botId}")
    public BotResponse updateBot(
            @PathVariable Long botId,
            @Valid @RequestBody UpdateBotRequest request
    ) {

        return botService.updateBot(
                botId,
                request
        );
    }

    @DeleteMapping("/{botId}")
    public void deleteBot(
            @PathVariable Long botId
    ) {

        botDeletionService.deleteBot(
                botId
        );
    }

    @GetMapping("/running")
    public List<RunningBotResponse> getRunningBotIds() {

        return botService.getRunningBotIds();
    }

    @PatchMapping("/{botId}/start")
    public void startBot(
            @PathVariable Long botId
    ) {

        botService.startBot(
                botId
        );
    }

    @PatchMapping("/{botId}/stop")
    public void stopBot(
            @PathVariable Long botId
    ) {

        botService.stopBot(
                botId
        );
    }

    @GetMapping("/{botId}/playwright")
    public BotPlaywrightResponse getPlaywrightBot(
            @PathVariable Long botId
    ) {

        return botService.getPlaywrightBot(
                botId
        );
    }
}

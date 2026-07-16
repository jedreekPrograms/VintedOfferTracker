package pl.flipbot.bot;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.bot.dto.BotPlaywrightResponse;
import pl.flipbot.bot.dto.BotResponse;
import pl.flipbot.bot.dto.CreateBotRequest;
import pl.flipbot.bot.dto.RunningBotResponse;

import java.util.List;

@RestController
@RequestMapping("/api/bots")
@RequiredArgsConstructor
public class BotController {

    private final BotService botService;

    @GetMapping
    public List<BotResponse> getAllBots() {
        return botService.getAllBots();
    }

    @PostMapping
    public BotResponse createBot(@Valid @RequestBody CreateBotRequest request) {

        return botService.createBot(request);
    }

    @GetMapping("/running")
    public List<RunningBotResponse> getRunningBotIds() {

        return botService.getRunningBotIds();

    }

    @PatchMapping("/{botId}/start")
    public void startBot(@PathVariable Long botId) {

        botService.startBot(botId);
    }

    @PatchMapping("/{botId}/stop")
    public void stopBot(@PathVariable Long botId) {

        botService.stopBot(botId);
    }

    @GetMapping("/{botId}/playwright")
    public BotPlaywrightResponse getPlaywrightBot(
            @PathVariable Long botId
    ) {


        return botService.getPlaywrightBot(botId);

    }
}

package pl.flipbot.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.bot.dto.CreateBotRequest;

import java.util.List;

@RestController
@RequestMapping("/api/bots")
@RequiredArgsConstructor
public class BotController {

    private final BotService botService;

    @GetMapping
    public List<Bot> getAllBots() {
        return botService.getAllBots();
    }

    @PostMapping
    public Bot createBot(@RequestBody CreateBotRequest request) {
        return botService.createBot(request);
    }
}

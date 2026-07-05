package pl.flipbot.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    public Bot createBot(@RequestBody Bot bot) {
        return botService.createBot(bot);
    }
}

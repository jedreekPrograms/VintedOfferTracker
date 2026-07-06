package pl.flipbot.bot;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.bot.dto.BotResponse;
import pl.flipbot.bot.dto.CreateBotRequest;

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
}

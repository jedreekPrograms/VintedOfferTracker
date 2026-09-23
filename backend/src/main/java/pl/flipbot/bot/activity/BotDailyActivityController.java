package pl.flipbot.bot.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.bot.activity.dto.BotDailyActivityResponse;

@RestController
@RequestMapping("/api/bots/{botId}/activity")
@RequiredArgsConstructor
public class BotDailyActivityController {

    private final BotDailyActivityService botDailyActivityService;

    @GetMapping("/today")
    public BotDailyActivityResponse getToday(
            @PathVariable Long botId
    ) {
        return botDailyActivityService.getToday(botId);
    }
}

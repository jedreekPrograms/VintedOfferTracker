package pl.flipbot.bot.activity;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.bot.activity.dto.BotDailyActivityResponse;

import java.util.List;

@RestController
@RequestMapping("/api/bots/activity")
@RequiredArgsConstructor
public class BotDailyActivitySnapshotController {

    private final BotDailyActivitySnapshotService snapshotService;

    @GetMapping("/today")
    public List<BotDailyActivityResponse> getToday() {
        return snapshotService.getToday();
    }
}

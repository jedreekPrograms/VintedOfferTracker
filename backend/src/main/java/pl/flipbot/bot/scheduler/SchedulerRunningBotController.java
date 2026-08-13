package pl.flipbot.bot.scheduler;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.flipbot.bot.dto.RunningBotResponse;

import java.util.List;

@RestController
@RequestMapping("/api/scheduler/bots")
@RequiredArgsConstructor
public class SchedulerRunningBotController {

    private final SchedulerRunningBotService schedulerRunningBotService;

    @GetMapping("/running")
    public List<RunningBotResponse> getRunningBots() {
        return schedulerRunningBotService.getRunningBots();
    }
}

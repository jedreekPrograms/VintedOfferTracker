package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import pl.flipbot.bot.dto.BotPlaywrightResponse;

@RestController
@RequestMapping("/api/market-stats/observer-bots")
@RequiredArgsConstructor
public class MarketStatsObserverController {

    private final MarketStatsObserverService observerService;

    @GetMapping("/{botId}")
    public BotPlaywrightResponse getObserverBot(
            @PathVariable Long botId
    ) {
        return observerService.getObserverBot(botId);
    }
}

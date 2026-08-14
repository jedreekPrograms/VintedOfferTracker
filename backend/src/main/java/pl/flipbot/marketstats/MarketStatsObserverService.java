package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.dto.BotPlaywrightResponse;
import pl.flipbot.mapper.BotMapper;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class MarketStatsObserverService {

    private final BotRepository botRepository;
    private final BotMapper botMapper;

    public BotPlaywrightResponse getObserverBot(
            Long botId
    ) {
        if (botId == null || botId <= 0) {
            throw new IllegalArgumentException(
                    "Observer bot id must be positive."
            );
        }

        Bot bot = botRepository.findById(botId)
                .orElseThrow(
                        () -> new NoSuchElementException(
                                "Observer bot was not found: " + botId
                        )
                );

        return botMapper.mapPlaywright(bot);
    }
}

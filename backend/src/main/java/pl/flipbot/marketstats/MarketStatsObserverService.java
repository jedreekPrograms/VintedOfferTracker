package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.dto.BotPlaywrightResponse;
import pl.flipbot.mapper.BotMapper;

import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class MarketStatsObserverService {

    private final BotRepository botRepository;
    private final BotMapper botMapper;

    @Transactional
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

        if (bot.getStatus() != BotStatus.STOPPED) {
            throw new IllegalStateException(
                    "Market statistics observer bot must remain STOPPED so it cannot share its Vinted session with the normal scheduler."
            );
        }

        for (Bot candidate : botRepository.findAll()) {
            boolean shouldBeObserver = candidate.getId().equals(botId);

            if (!Boolean.valueOf(shouldBeObserver).equals(
                    candidate.getMarketStatsObserver()
            )) {
                candidate.setMarketStatsObserver(shouldBeObserver);
            }
        }

        return botMapper.mapPlaywright(bot);
    }
}

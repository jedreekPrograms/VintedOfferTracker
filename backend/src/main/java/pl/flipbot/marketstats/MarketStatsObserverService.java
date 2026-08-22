package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.marketstats.dto.MarketStatsObserverPlaywrightResponse;
import pl.flipbot.marketstats.dto.MarketStatsObserverResponse;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MarketStatsObserverService {

    private final BotRepository botRepository;

    @Transactional(readOnly = true)
    public Optional<MarketStatsObserverResponse> getObserver() {
        return botRepository
                .findFirstByMarketStatsObserverTrue()
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Optional<MarketStatsObserverPlaywrightResponse> getObserverForPlaywright() {
        return botRepository
                .findFirstByMarketStatsObserverTrue()
                .map(observer -> new MarketStatsObserverPlaywrightResponse(
                        observer.getId(),
                        observer.getName(),
                        null,
                        null
                ));
    }

    private MarketStatsObserverResponse toResponse(
            Bot observer
    ) {
        return new MarketStatsObserverResponse(
                observer.getId(),
                observer.getName(),
                null
        );
    }
}

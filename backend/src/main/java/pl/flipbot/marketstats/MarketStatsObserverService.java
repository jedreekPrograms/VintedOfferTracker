package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.marketstats.dto.MarketStatsObserverPlaywrightResponse;
import pl.flipbot.marketstats.dto.MarketStatsObserverResponse;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketStatsObserverService {

    private static final String ANONYMOUS_OBSERVER_NAME =
            "Anonymous Market Observer";

    private final BotRepository botRepository;

    @Transactional
    public Optional<MarketStatsObserverResponse> getObserver() {
        return Optional.of(
                toResponse(ensureAnonymousObserver())
        );
    }

    @Transactional
    public Optional<MarketStatsObserverPlaywrightResponse> getObserverForPlaywright() {
        Bot observer = ensureAnonymousObserver();

        return Optional.of(
                new MarketStatsObserverPlaywrightResponse(
                        observer.getId(),
                        ANONYMOUS_OBSERVER_NAME,
                        null,
                        null
                )
        );
    }

    /**
     * Market statistics are collected from the public catalog and must never
     * depend on a user's Vinted session. Some stabilized local databases run
     * with Flyway disabled, so the migration that converted the old observer
     * account into an anonymous technical row may not have been applied.
     *
     * Normalize the observer on every read. This prevents Playwright from
     * restoring an obsolete sessions/bot-X.json for the observer and also
     * recreates the technical row automatically when it is missing.
     */
    private synchronized Bot ensureAnonymousObserver() {
        Bot observer = botRepository
                .findFirstByMarketStatsObserverTrue()
                .orElse(null);

        if (observer == null) {
            Bot created = Bot.builder()
                    .name(ANONYMOUS_OBSERVER_NAME)
                    .email(null)
                    .password(null)
                    .status(BotStatus.STOPPED)
                    .marketStatsObserver(true)
                    .build();

            Bot saved = botRepository.save(created);

            log.warn(
                    "[MARKET STATS] Anonymous observer row was missing. Created internal observer id={} automatically.",
                    saved.getId()
            );

            return saved;
        }

        boolean changed = false;

        if (!ANONYMOUS_OBSERVER_NAME.equals(observer.getName())) {
            observer.setName(ANONYMOUS_OBSERVER_NAME);
            changed = true;
        }

        if (observer.getEmail() != null) {
            observer.setEmail(null);
            changed = true;
        }

        if (observer.getPassword() != null) {
            observer.setPassword(null);
            changed = true;
        }

        if (observer.getStatus() != BotStatus.STOPPED) {
            observer.setStatus(BotStatus.STOPPED);
            changed = true;
        }

        if (!Boolean.TRUE.equals(observer.getMarketStatsObserver())) {
            observer.setMarketStatsObserver(true);
            changed = true;
        }

        if (!changed) {
            return observer;
        }

        Bot saved = botRepository.save(observer);

        log.warn(
                "[MARKET STATS] Normalized observer id={} to the credential-free anonymous runtime identity. "
                        + "Legacy observer credentials and stored login sessions will no longer be used for market collection.",
                saved.getId()
        );

        return saved;
    }

    private MarketStatsObserverResponse toResponse(
            Bot observer
    ) {
        return new MarketStatsObserverResponse(
                observer.getId(),
                ANONYMOUS_OBSERVER_NAME,
                null
        );
    }
}

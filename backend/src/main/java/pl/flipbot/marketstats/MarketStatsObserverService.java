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
     * The market observer is an internal, credential-free identity used only
     * to satisfy the existing bot-shaped Playwright contract. Local FlipBot
     * databases are commonly run with Flyway disabled, so V30 may not have
     * renamed/cleaned/created this row even though the Java code already
     * expects the anonymous observer lifecycle.
     *
     * Keep the runtime self-healing: every observer read makes the database
     * converge to the canonical anonymous row, and a missing row is created
     * automatically. Synchronization prevents the UI and Playwright startup
     * from racing each other into two observer rows in the same backend JVM.
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
                "[MARKET STATS] Normalized observer id={} to credential-free anonymous runtime identity. "
                        + "Any legacy observer login/session will no longer be used for market collection.",
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

package pl.flipbot.marketstats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.exception.BotAlreadyExistsException;
import pl.flipbot.marketstats.dto.CreateMarketStatsObserverRequest;
import pl.flipbot.marketstats.dto.MarketStatsObserverPlaywrightResponse;
import pl.flipbot.marketstats.dto.MarketStatsObserverResponse;
import pl.flipbot.marketstats.dto.UpdateMarketStatsObserverRequest;

import java.util.NoSuchElementException;
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

    @Transactional
    public MarketStatsObserverResponse createObserver(
            CreateMarketStatsObserverRequest request
    ) {
        if (botRepository.findFirstByMarketStatsObserverTrue().isPresent()) {
            throw new IllegalStateException(
                    "A market statistics observer already exists."
            );
        }

        String email = normalizeRequiredText(request.email());

        if (botRepository.existsByEmail(email)) {
            throw new BotAlreadyExistsException(email);
        }

        Bot observer = Bot.builder()
                .name(normalizeRequiredText(request.name()))
                .email(email)
                .password(request.password())
                .status(BotStatus.STOPPED)
                .marketStatsObserver(true)
                .build();

        return toResponse(
                botRepository.save(observer)
        );
    }

    @Transactional
    public MarketStatsObserverResponse updateObserver(
            UpdateMarketStatsObserverRequest request
    ) {
        Bot observer = requireObserver();
        String email = normalizeRequiredText(request.email());

        if (botRepository.existsByEmailAndIdNot(
                email,
                observer.getId()
        )) {
            throw new BotAlreadyExistsException(email);
        }

        observer.setName(
                normalizeRequiredText(request.name())
        );
        observer.setEmail(email);

        if (request.password() != null
                && !request.password().isBlank()) {
            observer.setPassword(request.password());
        }

        observer.setStatus(BotStatus.STOPPED);
        observer.setMarketStatsObserver(true);

        return toResponse(observer);
    }

    @Transactional
    public void deleteObserver() {
        botRepository.delete(
                requireObserver()
        );
    }

    @Transactional(readOnly = true)
    public Optional<MarketStatsObserverPlaywrightResponse> getObserverForPlaywright() {
        return botRepository
                .findFirstByMarketStatsObserverTrue()
                .map(observer -> new MarketStatsObserverPlaywrightResponse(
                        observer.getId(),
                        observer.getName(),
                        observer.getEmail(),
                        observer.getPassword()
                ));
    }

    private Bot requireObserver() {
        return botRepository
                .findFirstByMarketStatsObserverTrue()
                .orElseThrow(
                        () -> new NoSuchElementException(
                                "Market statistics observer does not exist."
                        )
                );
    }

    private MarketStatsObserverResponse toResponse(
            Bot observer
    ) {
        return new MarketStatsObserverResponse(
                observer.getId(),
                observer.getName(),
                observer.getEmail()
        );
    }

    private String normalizeRequiredText(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Observer name and e-mail cannot be blank."
            );
        }

        return value.trim();
    }
}

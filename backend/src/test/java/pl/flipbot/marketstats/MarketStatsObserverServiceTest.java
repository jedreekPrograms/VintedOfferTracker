package pl.flipbot.marketstats;

import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.marketstats.dto.MarketStatsObserverPlaywrightResponse;
import pl.flipbot.marketstats.dto.MarketStatsObserverResponse;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketStatsObserverServiceTest {

    @Test
    void legacyObserverIsNormalizedBeforePlaywrightReceivesIt() {
        BotRepository repository = mock(BotRepository.class);
        Bot legacyObserver = Bot.builder()
                .id(77L)
                .name("Legacy observer")
                .email("legacy-observer@example.com")
                .password("legacy-secret")
                .status(BotStatus.RUNNING)
                .marketStatsObserver(true)
                .build();

        when(repository.findFirstByMarketStatsObserverTrue())
                .thenReturn(Optional.of(legacyObserver));
        when(repository.save(any(Bot.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MarketStatsObserverService service =
                new MarketStatsObserverService(repository);

        MarketStatsObserverPlaywrightResponse response = service
                .getObserverForPlaywright()
                .orElseThrow();

        assertEquals(77L, response.id());
        assertEquals("Anonymous Market Observer", response.name());
        assertNull(response.email());
        assertNull(response.password());

        assertEquals("Anonymous Market Observer", legacyObserver.getName());
        assertNull(legacyObserver.getEmail());
        assertNull(legacyObserver.getPassword());
        assertEquals(BotStatus.STOPPED, legacyObserver.getStatus());
        assertTrue(legacyObserver.getMarketStatsObserver());
        verify(repository).save(legacyObserver);
    }

    @Test
    void missingObserverIsCreatedAutomatically() {
        BotRepository repository = mock(BotRepository.class);

        when(repository.findFirstByMarketStatsObserverTrue())
                .thenReturn(Optional.empty());
        when(repository.save(any(Bot.class)))
                .thenAnswer(invocation -> {
                    Bot observer = invocation.getArgument(0);
                    observer.setId(91L);
                    return observer;
                });

        MarketStatsObserverService service =
                new MarketStatsObserverService(repository);

        MarketStatsObserverResponse response = service
                .getObserver()
                .orElseThrow();

        assertEquals(91L, response.id());
        assertEquals("Anonymous Market Observer", response.name());
        assertNull(response.email());

        Bot saved = org.mockito.ArgumentCaptor
                .forClass(Bot.class)
                .getValue();
    }
}

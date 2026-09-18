package pl.flipbot.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.flipbot.bot.runtime.BotRuntimeState;
import pl.flipbot.bot.runtime.BotRuntimeStateRepository;
import pl.flipbot.bot.runtime.BotRuntimeStatus;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.guard.RealActionGuardRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotDeletionServiceTest {

    private BotRepository botRepository;
    private ListingRepository listingRepository;
    private RealActionGuardRepository guardRepository;
    private BotRuntimeStateRepository runtimeStateRepository;
    private JdbcTemplate jdbcTemplate;
    private BotDeletionService service;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        listingRepository = mock(ListingRepository.class);
        guardRepository = mock(RealActionGuardRepository.class);
        runtimeStateRepository = mock(BotRuntimeStateRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        service = new BotDeletionService(
                botRepository,
                listingRepository,
                guardRepository,
                runtimeStateRepository,
                jdbcTemplate
        );

        Bot bot = Bot.builder()
                .id(5L)
                .name("Delete candidate")
                .status(BotStatus.STOPPED)
                .build();

        when(botRepository.findById(5L)).thenReturn(Optional.of(bot));
        when(runtimeStateRepository.findById(5L)).thenReturn(Optional.empty());
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                5L,
                ListingStatus.NEGOTIATING
        )).thenReturn(List.of());
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                5L,
                ListingStatus.ACTION_REQUIRED
        )).thenReturn(List.of());
        when(guardRepository.countUnresolvedByBotId(5L)).thenReturn(0L);
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq(5L)
        )).thenReturn(false);
    }

    @Test
    void workingRuntimeBlocksDeletionEvenAfterBotWasStopped() {
        BotRuntimeState state = new BotRuntimeState();
        state.setRuntimeStatus(BotRuntimeStatus.WORKING);
        when(runtimeStateRepository.findById(5L)).thenReturn(Optional.of(state));

        assertThrows(
                IllegalStateException.class,
                () -> service.deleteBot(5L)
        );

        verify(botRepository, never()).delete(
                org.mockito.ArgumentMatchers.any(Bot.class)
        );
    }

    @Test
    void unresolvedRealActionGuardBlocksDeletion() {
        when(guardRepository.countUnresolvedByBotId(5L))
                .thenReturn(1L);

        assertThrows(
                IllegalStateException.class,
                () -> service.deleteBot(5L)
        );

        verify(botRepository, never()).delete(
                org.mockito.ArgumentMatchers.any(Bot.class)
        );
    }

    @Test
    void unconfirmedMarketplaceClaimBlocksDeletionEvenWithoutGuard() {
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq(5L)
        )).thenReturn(true);

        assertThrows(
                IllegalStateException.class,
                () -> service.deleteBot(5L)
        );

        verify(botRepository, never()).delete(
                org.mockito.ArgumentMatchers.any(Bot.class)
        );
    }

    @Test
    void confirmedStaleGuardDoesNotBlockDeletion() {
        when(guardRepository.countUnresolvedByBotId(5L))
                .thenReturn(0L);

        service.deleteBot(5L);

        verify(botRepository).delete(
                org.mockito.ArgumentMatchers.argThat(
                        bot -> bot != null && Long.valueOf(5L).equals(bot.getId())
                )
        );
    }

    @Test
    void stoppedBotWithoutActiveListingsOrUnresolvedActionsCanBeDeleted() {
        service.deleteBot(5L);

        verify(botRepository).delete(
                org.mockito.ArgumentMatchers.argThat(
                        bot -> bot != null && Long.valueOf(5L).equals(bot.getId())
                )
        );
    }
}

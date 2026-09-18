package pl.flipbot.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private JdbcTemplate jdbcTemplate;
    private BotDeletionService service;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        listingRepository = mock(ListingRepository.class);
        guardRepository = mock(RealActionGuardRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        service = new BotDeletionService(
                botRepository,
                listingRepository,
                guardRepository,
                jdbcTemplate
        );

        Bot bot = Bot.builder()
                .id(5L)
                .name("Delete candidate")
                .status(BotStatus.STOPPED)
                .build();

        when(botRepository.findById(5L)).thenReturn(Optional.of(bot));
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                5L,
                ListingStatus.NEGOTIATING
        )).thenReturn(List.of());
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(
                5L,
                ListingStatus.ACTION_REQUIRED
        )).thenReturn(List.of());
        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Boolean.class),
                eq(5L)
        )).thenReturn(false);
    }

    @Test
    void unresolvedRealActionGuardBlocksDeletion() {
        when(guardRepository.countByListing_Bot_Id(5L))
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
        when(guardRepository.countByListing_Bot_Id(5L))
                .thenReturn(0L);
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
    void stoppedBotWithoutActiveListingsOrUnresolvedActionsCanBeDeleted() {
        when(guardRepository.countByListing_Bot_Id(5L))
                .thenReturn(0L);

        service.deleteBot(5L);

        verify(botRepository).delete(
                org.mockito.ArgumentMatchers.argThat(
                        bot -> bot != null && Long.valueOf(5L).equals(bot.getId())
                )
        );
    }
}

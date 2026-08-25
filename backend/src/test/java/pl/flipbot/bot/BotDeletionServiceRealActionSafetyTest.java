package pl.flipbot.bot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.negotiation.guard.RealActionGuardRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BotDeletionServiceRealActionSafetyTest {

    private BotRepository botRepository;
    private ListingRepository listingRepository;
    private RealActionGuardRepository guardRepository;
    private JdbcTemplate jdbcTemplate;
    private BotDeletionService service;
    private Bot stoppedBot;

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

        stoppedBot = mock(Bot.class);
        when(stoppedBot.getStatus()).thenReturn(BotStatus.STOPPED);
        when(botRepository.findById(1L)).thenReturn(Optional.of(stoppedBot));
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(1L, ListingStatus.NEGOTIATING))
                .thenReturn(List.of());
        when(listingRepository.findByBotIdAndStatusOrderByIdAsc(1L, ListingStatus.ACTION_REQUIRED))
                .thenReturn(List.of());
    }

    @Test
    void activeRealActionGuardPreventsBotDeletion() {
        when(guardRepository.existsByListing_Bot_Id(1L)).thenReturn(true);
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Boolean.class), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(false);

        assertThrows(
                IllegalStateException.class,
                () -> service.deleteBot(1L)
        );

        verify(botRepository, never()).delete(stoppedBot);
    }

    @Test
    void unconfirmedMarketplaceClaimPreventsBotDeletionEvenWithoutGuard() {
        when(guardRepository.existsByListing_Bot_Id(1L)).thenReturn(false);
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Boolean.class), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(true);

        assertThrows(
                IllegalStateException.class,
                () -> service.deleteBot(1L)
        );

        verify(botRepository, never()).delete(stoppedBot);
    }

    @Test
    void botWithoutActiveListingsGuardsOrUnconfirmedClaimsCanBeDeleted() {
        when(guardRepository.existsByListing_Bot_Id(1L)).thenReturn(false);
        when(jdbcTemplate.queryForObject(anyString(), org.mockito.ArgumentMatchers.eq(Boolean.class), org.mockito.ArgumentMatchers.eq(1L)))
                .thenReturn(false);

        assertDoesNotThrow(() -> service.deleteBot(1L));

        verify(botRepository).delete(stoppedBot);
    }
}

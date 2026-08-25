package pl.flipbot.negotiation.guard;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.marketplace.Marketplace;
import pl.flipbot.negotiation.audit.RealActionAuditService;
import pl.flipbot.negotiation.guard.dto.AcquireRealActionGuardRequest;
import pl.flipbot.negotiation.guard.dto.ReleaseRealActionGuardRequest;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RealActionGuardCrossBotClaimTest {

    private ListingRepository listingRepository;
    private RealActionGuardRepository guardRepository;
    private JdbcTemplate jdbcTemplate;
    private RealActionGuardService service;

    @BeforeEach
    void setUp() {
        listingRepository = mock(ListingRepository.class);
        guardRepository = mock(RealActionGuardRepository.class);
        RealActionAuditService auditService = mock(RealActionAuditService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new RealActionGuardService(
                listingRepository,
                guardRepository,
                auditService,
                jdbcTemplate
        );
    }

    @Test
    void temporaryClaimBlocksAttemptWithoutMakingLosingListingTerminal() {
        Listing listing = listing(22L, 2L, "9717432736", 0);
        UUID requestId = UUID.randomUUID();

        when(listingRepository.findByIdAndBotIdForUpdate(22L, 2L))
                .thenReturn(Optional.of(listing));
        when(guardRepository.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(guardRepository.findByListing_Id(22L)).thenReturn(Optional.empty());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(
                Map.of(
                        "owner_bot_id", 1L,
                        "owner_listing_id", 11L,
                        "request_id", UUID.randomUUID(),
                        "claimed_at", LocalDateTime.now()
                )
        );

        var response = service.acquire(
                2L,
                22L,
                new AcquireRealActionGuardRequest(
                        requestId,
                        RealActionType.FIRST_OFFER,
                        1
                )
        );

        assertFalse(response.acquired());
        assertEquals(ListingStatus.DISCOVERED, listing.getStatus());
        verify(listingRepository, never()).saveAndFlush(listing);
        verify(guardRepository, never()).saveAndFlush(any(RealActionGuard.class));
    }

    @Test
    void confirmedClaimMakesLosingListingTerminal() {
        Listing listing = listing(22L, 2L, "9717432736", 0);
        UUID requestId = UUID.randomUUID();

        when(listingRepository.findByIdAndBotIdForUpdate(22L, 2L))
                .thenReturn(Optional.of(listing));
        when(guardRepository.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(guardRepository.findByListing_Id(22L)).thenReturn(Optional.empty());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class))).thenReturn(
                Map.of(
                        "owner_bot_id", 1L,
                        "owner_listing_id", 11L,
                        "request_id", UUID.randomUUID(),
                        "claimed_at", LocalDateTime.now(),
                        "confirmed_at", LocalDateTime.now()
                )
        );

        var response = service.acquire(
                2L,
                22L,
                new AcquireRealActionGuardRequest(
                        requestId,
                        RealActionType.FIRST_OFFER,
                        1
                )
        );

        assertFalse(response.acquired());
        assertEquals(ListingStatus.SKIPPED_ALREADY_NEGOTIATED, listing.getStatus());
        verify(listingRepository).saveAndFlush(listing);
        verify(guardRepository, never()).saveAndFlush(any(RealActionGuard.class));
    }

    @Test
    void preSubmitClaimReleaseOnlyDeletesReservationAndNeedsNoReopenPass() {
        Listing ownerListing = listing(11L, 1L, "9717432736", 0);
        UUID requestId = UUID.randomUUID();
        RealActionGuard guard = RealActionGuard.builder()
                .listing(ownerListing)
                .requestId(requestId)
                .actionType(RealActionType.FIRST_OFFER)
                .stepNumber(1)
                .createdAt(LocalDateTime.now())
                .build();

        when(listingRepository.findByIdAndBotIdForUpdate(11L, 1L))
                .thenReturn(Optional.of(ownerListing));
        when(guardRepository.findByListing_Id(11L))
                .thenReturn(Optional.of(guard));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        var response = service.release(
                1L,
                11L,
                new ReleaseRealActionGuardRequest(requestId)
        );

        assertTrue(response.released());
        assertFalse(response.alreadyAbsent());
        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
        verify(guardRepository).delete(guard);
        verify(guardRepository).flush();
    }

    @Test
    void confirmedFirstOfferMarksMarketplaceClaimDurableBeforeGuardCleanup() {
        Listing ownerListing = listing(11L, 1L, "9717432736", 1);
        UUID requestId = UUID.randomUUID();
        RealActionGuard guard = RealActionGuard.builder()
                .listing(ownerListing)
                .requestId(requestId)
                .actionType(RealActionType.FIRST_OFFER)
                .stepNumber(1)
                .createdAt(LocalDateTime.now())
                .build();

        when(listingRepository.findByIdAndBotIdForUpdate(11L, 1L))
                .thenReturn(Optional.of(ownerListing));
        when(guardRepository.findByListing_Id(11L))
                .thenReturn(Optional.of(guard));
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        var response = service.release(
                1L,
                11L,
                new ReleaseRealActionGuardRequest(requestId)
        );

        assertTrue(response.released());
        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
        verify(guardRepository).delete(guard);
        verify(guardRepository).flush();
    }

    private Listing listing(
            Long listingId,
            Long botId,
            String marketplaceListingId,
            int currentStep
    ) {
        BotConfiguration configuration = BotConfiguration.builder()
                .marketplace(Marketplace.VINTED)
                .build();
        Bot bot = Bot.builder().id(botId).configuration(configuration).build();
        configuration.setBot(bot);
        return Listing.builder()
                .id(listingId)
                .listingId(marketplaceListingId)
                .status(ListingStatus.DISCOVERED)
                .currentStep(currentStep)
                .bot(bot)
                .build();
    }
}

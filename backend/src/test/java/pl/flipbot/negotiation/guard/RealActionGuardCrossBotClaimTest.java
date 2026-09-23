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

import java.time.LocalDateTime;
import java.util.HashMap;
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
    void firstOfferTemporaryConflictDoesNotLetSecondBotAcquireGuard() {
        Listing listing = listing(22L, 2L, "9755800886", ListingStatus.DISCOVERED, 0);
        UUID requestId = UUID.randomUUID();

        prepareNoLocalGuard(listing, requestId);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(owner(1L, 11L, null));

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
    void firstOfferConfirmedConflictMakesSecondBotTerminal() {
        Listing listing = listing(22L, 2L, "9755800886", ListingStatus.DISCOVERED, 0);
        UUID requestId = UUID.randomUUID();

        prepareNoLocalGuard(listing, requestId);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(owner(1L, 11L, LocalDateTime.now()));

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
    void nextStepIsBlockedWhenAnotherBotOwnsMarketplaceNegotiation() {
        Listing listing = listing(22L, 2L, "9755800886", ListingStatus.NEGOTIATING, 1);
        UUID requestId = UUID.randomUUID();

        prepareNoLocalGuard(listing, requestId);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(owner(1L, 11L, LocalDateTime.now()));

        var response = service.acquire(
                2L,
                22L,
                new AcquireRealActionGuardRequest(
                        requestId,
                        RealActionType.NEXT_STEP,
                        2
                )
        );

        assertFalse(response.acquired());
        assertEquals(ListingStatus.SKIPPED_ALREADY_NEGOTIATED, listing.getStatus());
        verify(listingRepository).saveAndFlush(listing);
        verify(guardRepository, never()).saveAndFlush(any(RealActionGuard.class));
    }

    @Test
    void firstBotCanAcquireGlobalClaimAndLocalGuard() {
        Listing listing = listing(11L, 1L, "9755800886", ListingStatus.DISCOVERED, 0);
        UUID requestId = UUID.randomUUID();

        prepareNoLocalGuard(listing, requestId);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        when(guardRepository.saveAndFlush(any(RealActionGuard.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.acquire(
                1L,
                11L,
                new AcquireRealActionGuardRequest(
                        requestId,
                        RealActionType.FIRST_OFFER,
                        1
                )
        );

        assertTrue(response.acquired());
        assertEquals(requestId, response.requestId());
        verify(guardRepository).saveAndFlush(any(RealActionGuard.class));
    }

    private void prepareNoLocalGuard(Listing listing, UUID requestId) {
        when(listingRepository.findByIdAndBotIdForUpdate(
                listing.getId(),
                listing.getBot().getId()
        )).thenReturn(Optional.of(listing));
        when(guardRepository.findByRequestId(requestId)).thenReturn(Optional.empty());
        when(guardRepository.findByListing_Id(listing.getId())).thenReturn(Optional.empty());
    }

    private Map<String, Object> owner(
            long botId,
            long listingId,
            LocalDateTime confirmedAt
    ) {
        Map<String, Object> result = new HashMap<>();
        result.put("owner_bot_id", botId);
        result.put("owner_listing_id", listingId);
        result.put("request_id", UUID.randomUUID());
        result.put("claimed_at", LocalDateTime.now());
        result.put("confirmed_at", confirmedAt);
        return result;
    }

    private Listing listing(
            Long backendListingId,
            Long botId,
            String marketplaceListingId,
            ListingStatus status,
            int currentStep
    ) {
        BotConfiguration configuration = BotConfiguration.builder()
                .marketplace(Marketplace.VINTED)
                .build();

        Bot bot = Bot.builder()
                .id(botId)
                .configuration(configuration)
                .build();

        configuration.setBot(bot);

        return Listing.builder()
                .id(backendListingId)
                .listingId(marketplaceListingId)
                .status(status)
                .currentStep(currentStep)
                .bot(bot)
                .build();
    }
}

package pl.flipbot.probe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.probe.dto.PriceProbeAssignmentResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceProbeServiceTest {

    private BotRepository botRepository;
    private ListingRepository listingRepository;
    private PriceProbeRepository priceProbeRepository;
    private PriceProbeService service;

    @BeforeEach
    void setUp() {
        botRepository = mock(BotRepository.class);
        listingRepository = mock(ListingRepository.class);
        priceProbeRepository = mock(PriceProbeRepository.class);
        service = new PriceProbeService(
                botRepository,
                listingRepository,
                priceProbeRepository
        );

        when(priceProbeRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void doesNotProbeOwnListing() {
        Bot bot = bot(1L, "S25 #1");
        Listing source = listing(100L, bot, "1300.00");

        when(botRepository.findById(1L)).thenReturn(Optional.of(bot));
        when(listingRepository.findByStatusInOrderByIdAsc(any()))
                .thenReturn(List.of(source));

        assertTrue(service.claimNext(1L).isEmpty());
        verify(priceProbeRepository, never()).saveAndFlush(any());
    }

    @Test
    void probesListingOwnedByDifferentTargetBot() {
        Bot probeBot = bot(1L, "S25 #1");
        Bot sourceBot = bot(2L, "S26 #1");
        Listing source = listing(100L, sourceBot, "1300.00");

        when(botRepository.findById(1L)).thenReturn(Optional.of(probeBot));
        when(listingRepository.findByStatusInOrderByIdAsc(any()))
                .thenReturn(List.of(source));
        when(listingRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(source));
        when(priceProbeRepository.existsByProbeBot_IdAndSourceListing_Id(1L, 100L))
                .thenReturn(false);
        when(priceProbeRepository.countReservedSlots(100L))
                .thenReturn(0L);
        when(priceProbeRepository.saveAndFlush(any()))
                .thenAnswer(invocation -> {
                    PriceProbe probe = invocation.getArgument(0);
                    probe.setId(500L);
                    return probe;
                });

        Optional<PriceProbeAssignmentResponse> result = service.claimNext(1L);

        assertTrue(result.isPresent());
        assertEquals(500L, result.get().probeId());
        assertEquals(15, result.get().maximumProbeCount());
        assertTrue(
                result.get().probePrice().compareTo(new BigDecimal("1300.00")) < 0
        );
        assertTrue(result.get().message().contains("PLN"));
    }

    @Test
    void hardStopsAtFifteenReservedSlots() {
        Bot probeBot = bot(1L, "S25 #1");
        Bot sourceBot = bot(2L, "S11 Ultra #1");
        Listing source = listing(100L, sourceBot, "1500.00");

        when(botRepository.findById(1L)).thenReturn(Optional.of(probeBot));
        when(listingRepository.findByStatusInOrderByIdAsc(any()))
                .thenReturn(List.of(source));
        when(listingRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(source));
        when(priceProbeRepository.existsByProbeBot_IdAndSourceListing_Id(1L, 100L))
                .thenReturn(false);
        when(priceProbeRepository.countReservedSlots(100L))
                .thenReturn(15L);

        assertTrue(service.claimNext(1L).isEmpty());
        verify(priceProbeRepository, never()).saveAndFlush(any());
    }

    private Bot bot(Long id, String name) {
        return Bot.builder()
                .id(id)
                .name(name)
                .status(BotStatus.RUNNING)
                .marketStatsObserver(false)
                .build();
    }

    private Listing listing(
            Long id,
            Bot bot,
            String currentPrice
    ) {
        return Listing.builder()
                .id(id)
                .listingId("market-" + id)
                .title("Test listing")
                .url("https://source.example/items/" + id)
                .originalPrice(new BigDecimal("1600.00"))
                .currentPrice(new BigDecimal(currentPrice))
                .currentStep(1)
                .awaitingSellerResponse(true)
                .status(ListingStatus.NEGOTIATING)
                .bot(bot)
                .build();
    }
}

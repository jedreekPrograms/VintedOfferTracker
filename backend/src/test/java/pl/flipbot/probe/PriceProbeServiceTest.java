package pl.flipbot.probe;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.BotStatus;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.listing.Listing;
import pl.flipbot.listing.ListingRepository;
import pl.flipbot.listing.ListingStatus;
import pl.flipbot.marketplace.Marketplace;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    }

    @Test
    void doesNotProbeOwnListing() {
        Bot bot = bot(1L, "Galaxy S25");
        Listing source = listing(100L, bot);

        when(botRepository.findById(1L)).thenReturn(Optional.of(bot));
        when(listingRepository.findByStatusInOrderByIdAsc(any()))
                .thenReturn(List.of(source));

        assertTrue(service.claimNext(1L).isEmpty());
        verify(priceProbeRepository, never()).saveAndFlush(any());
    }

    @Test
    void doesNotProbeDifferentTarget() {
        Bot probeBot = bot(1L, "Galaxy S25");
        Bot sourceBot = bot(2L, "Galaxy S26");
        Listing source = listing(100L, sourceBot);

        when(botRepository.findById(1L)).thenReturn(Optional.of(probeBot));
        when(listingRepository.findByStatusInOrderByIdAsc(any()))
                .thenReturn(List.of(source));

        assertTrue(service.claimNext(1L).isEmpty());
        verify(priceProbeRepository, never()).saveAndFlush(any());
    }

    private Bot bot(Long id, String model) {
        BotConfiguration configuration = BotConfiguration.builder()
                .marketplace(Marketplace.VINTED)
                .categoryPath(new ArrayList<>(List.of("Elektronika", "Telefony")))
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model(model)
                .build();

        Bot bot = Bot.builder()
                .id(id)
                .status(BotStatus.RUNNING)
                .marketStatsObserver(false)
                .configuration(configuration)
                .build();
        configuration.setBot(bot);
        return bot;
    }

    private Listing listing(Long id, Bot bot) {
        return Listing.builder()
                .id(id)
                .listingId("market-123")
                .title("Samsung")
                .url("https://www.vinted.pl/items/123-samsung")
                .originalPrice(new BigDecimal("1600.00"))
                .currentPrice(new BigDecimal("1300.00"))
                .currentStep(1)
                .awaitingSellerResponse(true)
                .status(ListingStatus.NEGOTIATING)
                .bot(bot)
                .build();
    }
}

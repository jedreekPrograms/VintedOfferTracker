package pl.flipbot.listing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.bot.BotRepository;
import pl.flipbot.bot.configuration.BotAdditionalTarget;
import pl.flipbot.bot.configuration.BotConfiguration;
import pl.flipbot.bot.configuration.TargetMode;
import pl.flipbot.listing.dto.CreateListingRequest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListingClaimServiceTest {

    private ListingRepository listingRepository;
    private BotRepository botRepository;
    private ListingClaimService service;

    @BeforeEach
    void setUp() {
        listingRepository = mock(ListingRepository.class);
        botRepository = mock(BotRepository.class);
        service = new ListingClaimService(listingRepository, botRepository);
        when(listingRepository.saveAndFlush(any(Listing.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void mainClaimSnapshotsCurrentMainProductLabel() {
        BotConfiguration configuration = BotConfiguration.builder()
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("Galaxy S25")
                .build();
        Bot bot = Bot.builder()
                .id(4L)
                .configuration(configuration)
                .build();
        configuration.setBot(bot);

        when(botRepository.getReferenceById(4L)).thenReturn(bot);

        Listing listing = service.claimListing(4L, request());

        assertNull(listing.getAdditionalTarget());
        assertEquals("Samsung → Galaxy S25", listing.getProductTargetLabel());
    }

    @Test
    void additionalClaimSnapshotsAdditionalProductInsteadOfMain() {
        BotConfiguration configuration = BotConfiguration.builder()
                .brand("Samsung")
                .targetMode(TargetMode.VINTED_MODEL)
                .model("Galaxy S25")
                .build();
        Bot bot = Bot.builder()
                .id(4L)
                .configuration(configuration)
                .build();
        configuration.setBot(bot);

        BotAdditionalTarget target = BotAdditionalTarget.builder()
                .id(17L)
                .brand("Apple")
                .targetMode(TargetMode.SEARCH_QUERY)
                .searchQuery("iPhone 16 Pro 256 GB")
                .active(true)
                .build();

        when(botRepository.getReferenceById(4L)).thenReturn(bot);

        Listing listing = service.claimListing(4L, target, request());

        assertSame(target, listing.getAdditionalTarget());
        assertEquals("Apple → iPhone 16 Pro 256 GB", listing.getProductTargetLabel());
    }

    private CreateListingRequest request() {
        CreateListingRequest request = new CreateListingRequest();
        request.setListingId("123");
        request.setTitle("Telefon");
        request.setUrl("https://www.vinted.pl/items/123");
        request.setOriginalPrice(new BigDecimal("1800.00"));
        return request;
    }
}

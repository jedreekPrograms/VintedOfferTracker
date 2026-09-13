package pl.flipbot.playwright.processing;

import org.junit.jupiter.api.Test;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.DiscoverListingsRequestDto;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.scanner.model.Listing;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ListingProcessingServiceTest {

    @Test
    void defaultProcessingUsesTheListingClientsBoundScope() {
        BotContext context = mock(BotContext.class);
        BotDetailsDto bot = new BotDetailsDto();
        bot.setId(9L);
        when(context.getBot()).thenReturn(bot);

        ScopeRecordingListingClient client = new ScopeRecordingListingClient();
        ListingProcessingService service = new ListingProcessingService(
                context,
                client
        );

        Listing listing = new Listing();
        listing.setId("123");
        listing.setTitle("Phone");
        listing.setUrl("https://www.vinted.pl/items/123-phone");
        listing.setPrice(new BigDecimal("500"));

        service.process(List.of(listing));

        assertTrue(client.boundScopeMethodCalled);
        assertEquals(false, client.explicitScopeMethodCalled);
    }

    private static final class ScopeRecordingListingClient
            extends ListingClient {

        private boolean boundScopeMethodCalled;
        private boolean explicitScopeMethodCalled;

        @Override
        public List<ListingResponseDto> discoverListings(
                Long botId,
                DiscoverListingsRequestDto request
        ) {
            boundScopeMethodCalled = true;
            return List.of();
        }

        @Override
        public List<ListingResponseDto> discoverListings(
                Long botId,
                Long additionalTargetId,
                DiscoverListingsRequestDto request
        ) {
            explicitScopeMethodCalled = true;
            throw new AssertionError(
                    "Default processing must preserve the concrete ListingClient scope"
            );
        }
    }
}

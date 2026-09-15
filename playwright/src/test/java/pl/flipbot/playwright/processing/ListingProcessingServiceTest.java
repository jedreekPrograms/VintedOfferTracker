package pl.flipbot.playwright.processing;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.dto.DiscoverListingsRequestDto;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;

import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ListingProcessingServiceTest {

    @Test
    public void defaultProcessingUsesTheListingClientsBoundScope() {
        ScopeRecordingListingClient client = new ScopeRecordingListingClient();
        DiscoverListingsRequestDto request = new DiscoverListingsRequestDto(
                List.of()
        );

        ListingProcessingService.discoverUsingClientScope(
                client,
                9L,
                null,
                request
        );

        assertTrue(client.boundScopeMethodCalled);
        assertFalse(client.explicitScopeMethodCalled);
    }

    @Test
    public void explicitTargetStillUsesTheExplicitScopeMethod() {
        ScopeRecordingListingClient client = new ScopeRecordingListingClient();
        DiscoverListingsRequestDto request = new DiscoverListingsRequestDto(
                List.of()
        );

        ListingProcessingService.discoverUsingClientScope(
                client,
                9L,
                44L,
                request
        );

        assertFalse(client.boundScopeMethodCalled);
        assertTrue(client.explicitScopeMethodCalled);
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
            return List.of();
        }
    }
}

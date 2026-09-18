package pl.flipbot.listing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.listing.dto.ActionRequiredListingResponse;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActionRequiredListingServiceTest {

    private ListingRepository repository;
    private ActionRequiredListingService service;

    @BeforeEach
    void setUp() {
        repository = mock(ListingRepository.class);
        service = new ActionRequiredListingService(repository);
    }

    @Test
    void bulkReadPreservesBotAndNegotiatedListingFields() {
        var row = mock(ListingRepository.ActionRequiredRow.class);
        when(row.getId()).thenReturn(3914L);
        when(row.getListingId()).thenReturn("10034864145");
        when(row.getTitle()).thenReturn("Samsung Galaxy S25 FE");
        when(row.getUrl()).thenReturn("https://www.vinted.pl/items/10034864145-samsung-galaxy-s25-fe");
        when(row.getOriginalPrice()).thenReturn(new BigDecimal("1190.44"));
        when(row.getCurrentPrice()).thenReturn(new BigDecimal("1000.00"));
        when(row.getCurrentStep()).thenReturn(2);
        when(row.getAwaitingSellerResponse()).thenReturn(false);
        when(row.getStatus()).thenReturn(ListingStatus.ACTION_REQUIRED);
        when(row.getBotId()).thenReturn(10L);
        when(row.getBotName()).thenReturn("Samsung S25 FE");
        when(row.getProductTargetLabel()).thenReturn("Samsung → Galaxy S25 FE");

        when(repository.findActionRequiredRows(
                ListingStatus.ACTION_REQUIRED
        )).thenReturn(List.of(row));

        List<ActionRequiredListingResponse> result =
                service.getAll();

        assertEquals(1, result.size());
        assertEquals(10L, result.getFirst().botId());
        assertEquals("Samsung S25 FE", result.getFirst().botName());
        assertEquals("10034864145", result.getFirst().listing().getListingId());
        assertEquals(new BigDecimal("1000.00"), result.getFirst().listing().getCurrentPrice());
        assertEquals("ACTION_REQUIRED", result.getFirst().listing().getStatus());
        assertEquals("Samsung → Galaxy S25 FE", result.getFirst().listing().getProductTargetLabel());

        verify(repository).findActionRequiredRows(
                ListingStatus.ACTION_REQUIRED
        );
    }

    @Test
    void countUsesSingleStatusCounter() {
        when(repository.countByStatus(
                ListingStatus.ACTION_REQUIRED
        )).thenReturn(7L);

        assertEquals(7L, service.count());

        verify(repository).countByStatus(
                ListingStatus.ACTION_REQUIRED
        );
    }
}

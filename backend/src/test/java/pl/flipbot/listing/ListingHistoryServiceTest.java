package pl.flipbot.listing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.flipbot.bot.Bot;
import pl.flipbot.listing.dto.ListingHistoryResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ListingHistoryServiceTest {

    private ListingRepository listingRepository;
    private ListingHistoryService service;

    @BeforeEach
    void setUp() {
        listingRepository = mock(ListingRepository.class);
        service = new ListingHistoryService(listingRepository);
    }

    @Test
    void hiddenEntriesAreNotReturnedInHistory() {
        Listing visible = listing(1L, ListingStatus.PURCHASED, "1100.00");
        Listing hidden = listing(2L, ListingStatus.SKIPPED_BY_USER, "1200.00");
        hidden.setHistoryHidden(true);

        when(listingRepository.findAll()).thenReturn(List.of(hidden, visible));

        List<ListingHistoryResponse> history = service.getHistory();

        assertEquals(1, history.size());
        assertEquals(1L, history.getFirst().getId());
    }

    @Test
    void purchasePriceCanBeCorrectedAfterManualNegotiation() {
        Listing purchased = listing(10L, ListingStatus.PURCHASED, "1100.00");
        when(listingRepository.findById(10L)).thenReturn(Optional.of(purchased));

        ListingHistoryResponse response = service.updatePurchasePrice(
                10L,
                new BigDecimal("750")
        );

        assertEquals(new BigDecimal("750.00"), purchased.getCurrentPrice());
        assertEquals(new BigDecimal("750.00"), response.getCurrentPrice());
        verify(listingRepository).save(purchased);
    }

    @Test
    void purchasePriceCannotBeEditedForSkippedEntry() {
        Listing skipped = listing(11L, ListingStatus.SKIPPED_BY_USER, "1100.00");
        when(listingRepository.findById(11L)).thenReturn(Optional.of(skipped));

        assertThrows(
                IllegalStateException.class,
                () -> service.updatePurchasePrice(11L, new BigDecimal("750"))
        );
    }

    @Test
    void removingHistoryEntrySoftHidesInsteadOfDeletingListing() {
        Listing purchased = listing(12L, ListingStatus.PURCHASED, "1100.00");
        when(listingRepository.findById(12L)).thenReturn(Optional.of(purchased));

        assertFalse(purchased.isHistoryHidden());

        service.hideHistoryEntry(12L);

        assertTrue(purchased.isHistoryHidden());
        verify(listingRepository).save(purchased);
    }

    private Listing listing(
            Long id,
            ListingStatus status,
            String currentPrice
    ) {
        Bot bot = Bot.builder()
                .id(5L)
                .name("History bot")
                .build();

        return Listing.builder()
                .id(id)
                .listingId("market-" + id)
                .title("Samsung Galaxy S25")
                .url("https://www.vinted.pl/items/" + id)
                .originalPrice(new BigDecimal("1500.00"))
                .currentPrice(new BigDecimal(currentPrice))
                .currentStep(3)
                .awaitingSellerResponse(false)
                .status(status)
                .decisionAt(LocalDateTime.of(2026, 8, 24, 12, 0))
                .historyHidden(false)
                .bot(bot)
                .build();
    }
}

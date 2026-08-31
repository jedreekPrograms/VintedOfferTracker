package pl.flipbot.playwright.negotiation;

import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.BotConfigurationDto;

import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExistingNegotiationSupportTest {

    @Test
    public void friendlyErrorPreservesUsefulMultilinePlaywrightDetails() {
        ExistingNegotiationSupport support =
                new ExistingNegotiationSupport(null, null, null, null);

        String message = support.friendlyError(
                new RuntimeException(
                        "Error {\n  message='net::ERR_CONNECTION_RESET at https://www.vinted.pl/inbox/123'\n  name='Error'\n}"
                )
        );

        assertTrue(message.contains("ERR_CONNECTION_RESET"));
        assertTrue(message.contains("https://www.vinted.pl/inbox/123"));
        assertTrue(message.startsWith("Error {"));
    }

    @Test
    public void friendlyErrorHandlesMissingMessage() {
        ExistingNegotiationSupport support =
                new ExistingNegotiationSupport(null, null, null, null);

        assertEquals(
                "RuntimeException",
                support.friendlyError(new RuntimeException())
        );
    }

    @Test
    public void existingS25NegotiationRejectsConclusiveGalaxyTabMismatch() {
        ExistingNegotiationSupport support =
                new ExistingNegotiationSupport(null, null, null, null);

        assertFalse(
                support.matchesConfiguredTarget(
                        listing(
                                "Samsung Galaxy Tab S9 FE+",
                                "https://www.vinted.pl/items/9725937346-samsung-galaxy-tab-s9-fe"
                        ),
                        samsungS25()
                )
        );
    }

    @Test
    public void existingS25NegotiationRejectsFoldMismatchFromUrl() {
        ExistingNegotiationSupport support =
                new ExistingNegotiationSupport(null, null, null, null);

        assertFalse(
                support.matchesConfiguredTarget(
                        listing(
                                "Samsung telefon 256 GB",
                                "https://www.vinted.pl/items/123-samsung-galaxy-z-fold5"
                        ),
                        samsungS25()
                )
        );
    }

    @Test
    public void existingS25NegotiationKeepsGenericHistoricalTitleWhenNoConflictExists() {
        ExistingNegotiationSupport support =
                new ExistingNegotiationSupport(null, null, null, null);

        assertTrue(
                support.matchesConfiguredTarget(
                        listing(
                                "Telefon Samsung 128 GB",
                                "https://www.vinted.pl/items/123-telefon-samsung"
                        ),
                        samsungS25()
                )
        );
    }

    private BotConfigurationDto samsungS25() {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setTargetMode("VINTED_MODEL");
        configuration.setBrand("Samsung");
        configuration.setModel("Galaxy S25");
        return configuration;
    }

    private ListingResponseDto listing(String title, String url) {
        return new ListingResponseDto(
                1L,
                "123",
                title,
                url,
                new BigDecimal("1500.00"),
                new BigDecimal("900.00"),
                1,
                true,
                "conversation-1",
                "https://www.vinted.pl/inbox/conversation-1",
                "NEGOTIATING",
                null
        );
    }
}

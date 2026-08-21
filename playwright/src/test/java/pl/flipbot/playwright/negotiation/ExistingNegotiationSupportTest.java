package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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
}

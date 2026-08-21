package pl.flipbot.playwright.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BotApiClientTest {

    @Test
    public void diagnosticBodyIsCollapsedToOneLine() {
        assertEquals(
                "{ \"message\": \"temporary backend error\" }",
                BotApiClient.summarizeResponseBody(
                        "{\n  \"message\":   \"temporary backend error\"\n}"
                )
        );
    }

    @Test
    public void diagnosticBodyIsBounded() {
        String summary = BotApiClient.summarizeResponseBody("x".repeat(1_000));

        assertTrue(summary.endsWith("..."));
        assertTrue(summary.length() < 300);
    }

    @Test
    public void emptyDiagnosticBodyIsExplicit() {
        assertEquals("<empty>", BotApiClient.summarizeResponseBody("   "));
    }
}

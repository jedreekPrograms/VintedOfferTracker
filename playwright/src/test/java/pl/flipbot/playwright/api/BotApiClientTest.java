package pl.flipbot.playwright.api;

import org.junit.Test;

import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
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

    @Test
    public void parsesEveryPositiveBotIdWhileIgnoringOtherResponseFields() {
        BotApiClient client = new BotApiClient();

        Set<Long> ids = client.parseBotIds("""
                [
                  {"id": 7, "name": "stopped bot", "status": "STOPPED"},
                  {"id": 11, "name": "running bot", "status": "RUNNING"},
                  {"id": 0, "name": "invalid"},
                  {"name": "missing id"}
                ]
                """);

        assertEquals(Set.of(7L, 11L), ids);
    }

    @Test
    public void rejectsNonArrayAllBotPayload() {
        BotApiClient client = new BotApiClient();

        assertThrows(
                RuntimeException.class,
                () -> client.parseBotIds("{\"id\":7}")
        );
    }
}

package pl.flipbot.playwright.api;

import org.junit.Test;
import pl.flipbot.playwright.exception.ApiException;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ApiClientResourceLifecycleTest {

    @Test
    public void reusesSharedHttpClientAndObjectMapperAcrossApiClients() {
        TestApiClient first = new TestApiClient();
        TestApiClient second = new TestApiClient();

        assertSame(first.httpClient(), second.httpClient());
        assertSame(first.objectMapper(), second.objectMapper());
        assertEquals(
                Duration.ofSeconds(5),
                first.httpClient().connectTimeout().orElseThrow()
        );
    }

    @Test
    public void appliesFiniteTimeoutToEveryRequestBuilder() {
        TestApiClient client = new TestApiClient();

        HttpRequest request = client.buildRequest("/health");

        assertEquals(
                Duration.ofSeconds(20),
                request.timeout().orElseThrow()
        );
    }

    @Test
    public void restoresInterruptFlagWhenBlockingSendIsInterrupted() {
        TestApiClient client = new TestApiClient();

        try {
            Thread.currentThread().interrupt();

            try {
                client.callGet("/health");
                fail("Expected interrupted HTTP send to fail");
            } catch (ApiException exception) {
                assertTrue(
                        exception.getCause() instanceof InterruptedException
                );
                assertTrue(Thread.currentThread().isInterrupted());
            }
        } finally {
            Thread.interrupted();
        }
    }

    private static final class TestApiClient extends ApiClient {

        HttpClient httpClient() {
            return httpClient;
        }

        Object objectMapper() {
            return objectMapper;
        }

        HttpRequest buildRequest(String path) {
            return requestBuilder(path)
                    .GET()
                    .build();
        }

        void callGet(String path) {
            get(path);
        }
    }
}

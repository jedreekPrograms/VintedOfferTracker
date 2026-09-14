package pl.flipbot.playwright.api;

import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class BackendHttpTransportTest {

    @Test
    public void reusesOneProcessWideHttpClient() {
        HttpClient first = BackendHttpTransport.client();
        HttpClient second = BackendHttpTransport.client();

        assertSame(first, second);
        assertEquals(
                Duration.ofSeconds(5),
                first.connectTimeout().orElseThrow()
        );
    }

    @Test
    public void everyRequestGetsABoundedLifetime() {
        HttpRequest request = BackendHttpTransport.request(
                        URI.create("http://localhost:8081/test")
                )
                .GET()
                .build();

        assertEquals(
                Duration.ofSeconds(15),
                request.timeout().orElseThrow()
        );
    }
}

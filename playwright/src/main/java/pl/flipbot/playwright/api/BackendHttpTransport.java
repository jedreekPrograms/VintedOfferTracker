package pl.flipbot.playwright.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * Shared transport for Playwright -> local backend communication.
 *
 * <p>The scheduler creates many short-lived API client objects while processing
 * jobs. Keeping one process-wide HttpClient avoids repeatedly creating selector/
 * connection-pool resources, while bounded connect/request timeouts prevent a
 * local backend/socket stall from pinning a worker and its Chromium runtime
 * indefinitely.</p>
 */
public final class BackendHttpTransport {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    private BackendHttpTransport() {
    }

    public static HttpClient client() {
        return CLIENT;
    }

    public static HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(REQUEST_TIMEOUT);
    }
}

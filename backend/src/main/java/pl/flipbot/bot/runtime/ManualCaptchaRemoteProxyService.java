package pl.flipbot.bot.runtime;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class ManualCaptchaRemoteProxyService {

    private static final int DEFAULT_PLAYWRIGHT_PORT = 8092;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(3);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();

    public ResponseEntity<byte[]> getState(Long botId) {
        return proxyGet(botId, "/manual/browser/state", MediaType.APPLICATION_JSON);
    }

    public ResponseEntity<byte[]> getScreenshot(Long botId) {
        return proxyGet(botId, "/manual/browser/screenshot", MediaType.IMAGE_JPEG);
    }

    public ResponseEntity<byte[]> sendPointer(Long botId, byte[] json) {
        HttpRequest request = HttpRequest.newBuilder(uri("/manual/browser/pointer", botId))
                .timeout(REQUEST_TIMEOUT)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofByteArray(json))
                .build();
        return send(request, MediaType.TEXT_PLAIN);
    }

    private ResponseEntity<byte[]> proxyGet(Long botId, String path, MediaType fallbackType) {
        HttpRequest request = HttpRequest.newBuilder(uri(path, botId))
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return send(request, fallbackType);
    }

    private ResponseEntity<byte[]> send(HttpRequest request, MediaType fallbackType) {
        try {
            HttpResponse<byte[]> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            MediaType contentType = response.headers()
                    .firstValue(HttpHeaders.CONTENT_TYPE)
                    .map(MediaType::parseMediaType)
                    .orElse(fallbackType);

            return ResponseEntity.status(response.statusCode())
                    .contentType(contentType)
                    .cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailable("Playwright manual browser bridge request was interrupted");
        } catch (Exception exception) {
            return unavailable("Playwright manual browser bridge is unavailable");
        }
    }

    private ResponseEntity<byte[]> unavailable(String message) {
        return ResponseEntity.status(503)
                .contentType(MediaType.TEXT_PLAIN)
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .body(message.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private URI uri(String path, Long botId) {
        return URI.create(
                "http://127.0.0.1:" + configuredPort() + path + "?botId=" + botId
        );
    }

    private int configuredPort() {
        String raw = System.getenv("FLIPBOT_MANUAL_BROWSER_PORT");
        if (raw == null || raw.isBlank()) return DEFAULT_PLAYWRIGHT_PORT;
        int port = Integer.parseInt(raw.trim());
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("FLIPBOT_MANUAL_BROWSER_PORT must be between 1 and 65535");
        }
        return port;
    }
}

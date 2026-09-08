package pl.flipbot.playwright.verification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/** Loopback-only HTTP bridge used by the Spring API to reach the Playwright process. */
@Slf4j
public final class ManualBrowserControlServer implements AutoCloseable {

    private static final int DEFAULT_PORT = 8092;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RemoteManualBrowserSessionRegistry registry =
            RemoteManualBrowserSessionRegistry.getInstance();
    private final HttpServer server;

    public ManualBrowserControlServer() {
        try {
            int port = configuredPort();
            server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getLoopbackAddress(), port),
                    0
            );
            server.createContext("/manual/browser/state", this::handleState);
            server.createContext("/manual/browser/screenshot", this::handleScreenshot);
            server.createContext("/manual/browser/pointer", this::handlePointer);
            server.setExecutor(Executors.newCachedThreadPool(Thread.ofVirtual().factory()));
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create manual browser control server", exception);
        }
    }

    public void start() {
        server.start();
        log.info("[CAPTCHA REMOTE] Loopback manual browser bridge started on port {}.", server.getAddress().getPort());
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handleState(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        Long botId = requiredBotId(exchange.getRequestURI());
        byte[] json = objectMapper.writeValueAsBytes(registry.state(botId));
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, json.length);
        exchange.getResponseBody().write(json);
        exchange.close();
    }

    private void handleScreenshot(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        Long botId = requiredBotId(exchange.getRequestURI());
        RemoteManualBrowserSessionRegistry.FrameSnapshot frame = registry.frame(botId);
        if (frame == null) {
            sendText(exchange, 404, "Frame not ready");
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
        exchange.getResponseHeaders().set("Cache-Control", "no-store, max-age=0");
        exchange.getResponseHeaders().set("X-FlipBot-Frame-Version", Long.toString(frame.version()));
        exchange.sendResponseHeaders(200, frame.jpeg().length);
        exchange.getResponseBody().write(frame.jpeg());
        exchange.close();
    }

    private void handlePointer(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendText(exchange, 405, "Method not allowed");
            return;
        }

        Long botId = requiredBotId(exchange.getRequestURI());
        try {
            RemoteManualBrowserSessionRegistry.PointerCommand command =
                    objectMapper.readValue(
                            exchange.getRequestBody(),
                            RemoteManualBrowserSessionRegistry.PointerCommand.class
                    );
            registry.enqueue(botId, command);
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            sendText(exchange, 409, exception.getMessage());
        }
    }

    private Long requiredBotId(URI uri) {
        Map<String, String> query = parseQuery(uri.getRawQuery());
        String rawBotId = query.get("botId");
        if (rawBotId == null || rawBotId.isBlank()) {
            throw new IllegalArgumentException("Missing botId");
        }
        return Long.parseLong(rawBotId);
    }

    private Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> result = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank()) {
            return result;
        }
        for (String entry : rawQuery.split("&")) {
            String[] pair = entry.split("=", 2);
            if (pair.length == 2) {
                result.put(pair[0], pair[1]);
            }
        }
        return result;
    }

    private int configuredPort() {
        String raw = System.getenv("FLIPBOT_MANUAL_BROWSER_PORT");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PORT;
        }
        int port = Integer.parseInt(raw.trim());
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("FLIPBOT_MANUAL_BROWSER_PORT must be between 1 and 65535");
        }
        return port;
    }

    private void sendText(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = (message == null ? "" : message).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }
}

package pl.flipbot.playwright.lab.fingerprint;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loopback-only HTTP server for the defensive fingerprint laboratory.
 */
final class FingerprintLabServer implements AutoCloseable {

    static final int DEFAULT_PORT = 18091;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String ACCEPT_CH = String.join(
            ", ",
            "Sec-CH-UA-Platform-Version",
            "Sec-CH-UA-Arch",
            "Sec-CH-UA-Bitness",
            "Sec-CH-UA-Full-Version-List",
            "Sec-CH-UA-Model",
            "Sec-CH-UA-WoW64"
    );

    private final HttpServer server;
    private final byte[] indexHtml;

    private FingerprintLabServer(HttpServer server, byte[] indexHtml) {
        this.server = server;
        this.indexHtml = indexHtml;
    }

    static FingerprintLabServer startDefault() {
        return start(DEFAULT_PORT);
    }

    static FingerprintLabServer start(int port) {
        try {
            byte[] indexHtml = Files.readAllBytes(resolveIndexPath());
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(
                            InetAddress.getLoopbackAddress(),
                            port
                    ),
                    0
            );

            FingerprintLabServer labServer = new FingerprintLabServer(
                    server,
                    indexHtml
            );
            server.createContext("/api/headers", labServer::handleHeaders);
            server.createContext("/", labServer::handleIndex);
            server.start();
            return labServer;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not start fingerprint lab server",
                    exception
            );
        }
    }

    String url() {
        return "http://127.0.0.1:"
                + server.getAddress().getPort()
                + "/";
    }

    private void handleIndex(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        addCommonHeaders(exchange);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "text/html; charset=utf-8"
        );
        exchange.sendResponseHeaders(200, indexHtml.length);
        exchange.getResponseBody().write(indexHtml);
        exchange.close();
    }

    private void handleHeaders(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }

        Map<String, Object> selected = new LinkedHashMap<>();

        exchange.getRequestHeaders().forEach((name, values) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (lower.equals("user-agent")
                    || lower.startsWith("sec-ch-ua")) {
                selected.put(name, List.copyOf(values));
            }
        });

        byte[] body = OBJECT_MAPPER
                .writeValueAsString(selected)
                .getBytes(StandardCharsets.UTF_8);

        addCommonHeaders(exchange);
        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=utf-8"
        );
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private static void addCommonHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Accept-CH", ACCEPT_CH);
        exchange.getResponseHeaders().set(
                "Critical-CH",
                "Sec-CH-UA-Platform-Version, Sec-CH-UA-Arch, Sec-CH-UA-Bitness"
        );
        exchange.getResponseHeaders().set(
                "Content-Security-Policy",
                "default-src 'self' blob: data:; "
                        + "script-src 'self' 'unsafe-inline' blob:; "
                        + "style-src 'self' 'unsafe-inline'; "
                        + "connect-src 'self'; worker-src 'self' blob:"
        );
    }

    private static Path resolveIndexPath() {
        List<Path> candidates = List.of(
                Path.of("lab", "fingerprint", "index.html"),
                Path.of("playwright", "lab", "fingerprint", "index.html")
        );

        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        throw new IllegalStateException(
                "Could not find fingerprint lab page. Expected one of: "
                        + candidates
        );
    }

    @Override
    public void close() {
        server.stop(0);
    }
}

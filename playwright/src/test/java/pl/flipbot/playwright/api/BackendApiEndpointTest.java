package pl.flipbot.playwright.api;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackendApiEndpointTest {

    @Test
    void acceptsPlainHttpForIpv4Loopback() {
        assertEquals(
                URI.create("http://127.0.0.1:8080"),
                BackendApiEndpoint.resolve("http://127.0.0.1:8080/")
        );
    }

    @Test
    void acceptsPlainHttpForLocalhost() {
        assertEquals(
                URI.create("http://localhost:8080"),
                BackendApiEndpoint.resolve("http://localhost:8080")
        );
    }

    @Test
    void acceptsHttpsForRemoteBackend() {
        assertEquals(
                URI.create("https://flipbot.example:8443"),
                BackendApiEndpoint.resolve("https://flipbot.example:8443")
        );
    }

    @Test
    void rejectsPlainHttpForRemoteBackend() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BackendApiEndpoint.resolve("http://flipbot.example:8080")
        );
    }

    @Test
    void rejectsCredentialsEmbeddedInBackendUrl() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BackendApiEndpoint.resolve("https://user:secret@flipbot.example")
        );
    }

    @Test
    void rejectsBackendUrlWithPath() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BackendApiEndpoint.resolve("https://flipbot.example/internal")
        );
    }

    @Test
    void rejectsUnsupportedScheme() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BackendApiEndpoint.resolve("ftp://127.0.0.1:8080")
        );
    }
}

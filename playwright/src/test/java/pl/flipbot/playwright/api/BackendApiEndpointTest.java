package pl.flipbot.playwright.api;

import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class BackendApiEndpointTest {

    @Test
    public void acceptsPlainHttpForIpv4Loopback() {
        assertEquals(
                URI.create("http://127.0.0.1:8080"),
                BackendApiEndpoint.resolve("http://127.0.0.1:8080/")
        );
    }

    @Test
    public void acceptsPlainHttpForLocalhost() {
        assertEquals(
                URI.create("http://localhost:8080"),
                BackendApiEndpoint.resolve("http://localhost:8080")
        );
    }

    @Test
    public void acceptsHttpsForRemoteBackend() {
        assertEquals(
                URI.create("https://flipbot.example:8443"),
                BackendApiEndpoint.resolve("https://flipbot.example:8443")
        );
    }

    @Test
    public void rejectsPlainHttpForRemoteBackend() {
        assertIllegalArgument(
                () -> BackendApiEndpoint.resolve("http://flipbot.example:8080")
        );
    }

    @Test
    public void rejectsCredentialsEmbeddedInBackendUrl() {
        assertIllegalArgument(
                () -> BackendApiEndpoint.resolve("https://user:secret@flipbot.example")
        );
    }

    @Test
    public void rejectsBackendUrlWithPath() {
        assertIllegalArgument(
                () -> BackendApiEndpoint.resolve("https://flipbot.example/internal")
        );
    }

    @Test
    public void rejectsUnsupportedScheme() {
        assertIllegalArgument(
                () -> BackendApiEndpoint.resolve("ftp://127.0.0.1:8080")
        );
    }

    private void assertIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}

package pl.flipbot.playwright.lab.fingerprint;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FingerprintLabPolicyTest {

    @Test
    public void allowsOnlyLoopbackAndReservedTestHosts() {
        assertTrue(FingerprintLabPolicy.isAllowedUrl(
                "http://localhost:3000/fingerprint"
        ));
        assertTrue(FingerprintLabPolicy.isAllowedUrl(
                "https://127.0.0.1:8443/lab"
        ));
        assertTrue(FingerprintLabPolicy.isAllowedUrl(
                "http://fingerprint.test/demo"
        ));
        assertTrue(FingerprintLabPolicy.isAllowedUrl(
                "http://lab.localhost/demo"
        ));
    }

    @Test
    public void allowsLaboratoryWebSocketsOnly() {
        assertTrue(FingerprintLabPolicy.isAllowedWebSocketUrl(
                "ws://localhost:3000/socket"
        ));
        assertTrue(FingerprintLabPolicy.isAllowedWebSocketUrl(
                "wss://events.fingerprint.test/socket"
        ));

        assertFalse(FingerprintLabPolicy.isAllowedWebSocketUrl(
                "wss://www.vinted.pl/socket"
        ));
        assertFalse(FingerprintLabPolicy.isAllowedWebSocketUrl(
                "wss://example.com/socket"
        ));
    }

    @Test
    public void allowsOnlyLaboratoryProxyEndpoints() {
        assertTrue(FingerprintLabPolicy.isAllowedProxyUrl(
                "http://127.0.0.1:8888"
        ));
        assertTrue(FingerprintLabPolicy.isAllowedProxyUrl(
                "socks5://proxy.test:1080"
        ));

        assertFalse(FingerprintLabPolicy.isAllowedProxyUrl(
                "http://residential.example.com:3128"
        ));
        assertFalse(FingerprintLabPolicy.isAllowedProxyUrl(
                "socks5://proxy.vinted.pl:1080"
        ));
        assertFalse(FingerprintLabPolicy.isAllowedProxyUrl(
                "file:///tmp/proxy"
        ));
    }

    @Test
    public void rejectsMarketplaceAndArbitraryProductionHosts() {
        assertFalse(FingerprintLabPolicy.isAllowedUrl(
                "https://www.vinted.pl/"
        ));
        assertFalse(FingerprintLabPolicy.isAllowedUrl(
                "https://vinted.com/items/123"
        ));
        assertFalse(FingerprintLabPolicy.isAllowedUrl(
                "https://example.com/"
        ));
        assertFalse(FingerprintLabPolicy.isAllowedUrl(
                "https://fingerprint.test.example.com/"
        ));
        assertFalse(FingerprintLabPolicy.isAllowedUrl(
                "https://www.vinted.pl./"
        ));
    }

    @Test
    public void rejectsNonHttpSchemesAndMalformedUrls() {
        assertFalse(FingerprintLabPolicy.isAllowedUrl(
                "file:///tmp/fingerprint.html"
        ));
        assertFalse(FingerprintLabPolicy.isAllowedUrl(
                "javascript:alert(1)"
        ));
        assertFalse(FingerprintLabPolicy.isAllowedUrl(
                "not a url"
        ));
        assertFalse(FingerprintLabPolicy.isAllowedUrl(null));
    }
}

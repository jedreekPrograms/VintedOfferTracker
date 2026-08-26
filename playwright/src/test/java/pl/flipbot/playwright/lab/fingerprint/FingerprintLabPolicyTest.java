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

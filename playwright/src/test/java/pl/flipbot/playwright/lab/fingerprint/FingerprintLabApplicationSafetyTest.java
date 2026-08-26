package pl.flipbot.playwright.lab.fingerprint;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FingerprintLabApplicationSafetyTest {

    @Test
    public void allowsOnlyLaboratoryNetworkResources() {
        assertTrue(FingerprintLabApplication.isSafeLaboratoryResource(
                "http://127.0.0.1:3000/index.html"
        ));
        assertTrue(FingerprintLabApplication.isSafeLaboratoryResource(
                "https://fingerprint.test/app.js"
        ));
        assertTrue(FingerprintLabApplication.isSafeLaboratoryResource(
                "data:text/plain,hello"
        ));
        assertTrue(FingerprintLabApplication.isSafeLaboratoryResource(
                "blob:http://127.0.0.1:3000/abc"
        ));
        assertTrue(FingerprintLabApplication.isSafeLaboratoryResource(
                "about:blank"
        ));
    }

    @Test
    public void blocksProductionWebsitesIncludingVinted() {
        assertFalse(FingerprintLabApplication.isSafeLaboratoryResource(
                "https://www.vinted.pl/"
        ));
        assertFalse(FingerprintLabApplication.isSafeLaboratoryResource(
                "https://example.com/script.js"
        ));
        assertFalse(FingerprintLabApplication.isSafeLaboratoryResource(
                "https://fingerprint.test.example.com/"
        ));
    }
}

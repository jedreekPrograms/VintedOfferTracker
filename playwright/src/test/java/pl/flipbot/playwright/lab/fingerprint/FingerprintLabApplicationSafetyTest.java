package pl.flipbot.playwright.lab.fingerprint;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FingerprintLabApplicationSafetyTest {

    @Test
    public void allowsOnlyLaboratoryNetworkResources() {
        assertTrue(FingerprintLabRuntimeSupport.isSafeLaboratoryResource(
                "http://127.0.0.1:3000/index.html"
        ));
        assertTrue(FingerprintLabRuntimeSupport.isSafeLaboratoryResource(
                "https://fingerprint.test/app.js"
        ));
        assertTrue(FingerprintLabRuntimeSupport.isSafeLaboratoryResource(
                "data:text/plain,hello"
        ));
        assertTrue(FingerprintLabRuntimeSupport.isSafeLaboratoryResource(
                "blob:http://127.0.0.1:3000/abc"
        ));
        assertTrue(FingerprintLabRuntimeSupport.isSafeLaboratoryResource(
                "about:blank"
        ));
    }

    @Test
    public void blocksProductionWebsitesIncludingVinted() {
        assertFalse(FingerprintLabRuntimeSupport.isSafeLaboratoryResource(
                "https://www.vinted.pl/"
        ));
        assertFalse(FingerprintLabRuntimeSupport.isSafeLaboratoryResource(
                "https://example.com/script.js"
        ));
        assertFalse(FingerprintLabRuntimeSupport.isSafeLaboratoryResource(
                "https://fingerprint.test.example.com/"
        ));
    }
}

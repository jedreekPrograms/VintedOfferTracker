package pl.flipbot.playwright.lab.fingerprint;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class FingerprintLabRuntimeIntegrationTest {

    @Test
    public void remainsDisabledWhenRuntimeIntegrationIsNotRequested() {
        assertFalse(FingerprintLabRuntimeIntegration.validateConfiguration(
                false,
                false,
                "https://www.vinted.pl/"
        ));
    }

    @Test
    public void requiresTheExistingFingerprintLabFeatureGate() {
        assertRejected(
                true,
                false,
                "http://127.0.0.1:18091/"
        );
    }

    @Test
    public void activatesOnlyForAllowedLaboratoryTargets() {
        assertTrue(FingerprintLabRuntimeIntegration.validateConfiguration(
                true,
                true,
                "http://127.0.0.1:18091/"
        ));

        assertTrue(FingerprintLabRuntimeIntegration.validateConfiguration(
                true,
                true,
                "https://browser-check.test/fingerprint"
        ));
    }

    @Test
    public void rejectsVintedEvenWhenBothFeatureFlagsAreEnabled() {
        assertRejected(
                true,
                true,
                "https://www.vinted.pl/"
        );
        assertRejected(
                true,
                true,
                "https://vinted.com/items/123"
        );
    }

    @Test
    public void rejectsArbitraryProductionTargets() {
        assertRejected(
                true,
                true,
                "https://example.com/"
        );
        assertRejected(
                true,
                true,
                null
        );
        assertRejected(
                true,
                true,
                "   "
        );
    }

    private static void assertRejected(
            boolean runtimeIntegrationRequested,
            boolean fingerprintLabEnabled,
            String targetUrl
    ) {
        try {
            FingerprintLabRuntimeIntegration.validateConfiguration(
                    runtimeIntegrationRequested,
                    fingerprintLabEnabled,
                    targetUrl
            );
            fail("Expected fingerprint runtime configuration to be rejected");
        } catch (IllegalStateException expected) {
            // expected fail-closed behavior
        }
    }
}

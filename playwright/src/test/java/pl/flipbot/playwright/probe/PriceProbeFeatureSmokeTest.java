package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PriceProbeFeatureSmokeTest {
    @Test
    public void sandboxFeatureStateExists() {
        assertTrue(PriceProbeExecutionResult.State.values().length >= 3);
    }
}

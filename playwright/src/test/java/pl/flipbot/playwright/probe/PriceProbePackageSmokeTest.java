package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PriceProbePackageSmokeTest {

    @Test
    public void executionStateLoads() {
        assertNotNull(PriceProbeExecutionResult.State.SENT);
    }
}

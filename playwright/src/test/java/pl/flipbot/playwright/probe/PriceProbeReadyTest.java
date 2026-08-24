package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PriceProbeReadyTest {
    @Test
    public void executorTypeExists() {
        assertNotNull(PriceProbeExecutor.class);
    }
}

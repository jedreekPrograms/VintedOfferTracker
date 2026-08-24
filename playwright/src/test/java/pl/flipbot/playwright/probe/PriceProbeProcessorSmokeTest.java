package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class PriceProbeProcessorSmokeTest {
    @Test
    public void processorTypeExists() {
        assertNotNull(PriceProbeProcessor.class);
    }
}

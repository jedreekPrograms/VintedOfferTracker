package pl.flipbot.playwright.probe;

import org.junit.Test;
import pl.flipbot.playwright.worker.ScheduledJobType;

import static org.junit.Assert.assertEquals;

public class PriceProbeSchedulerTypeTest {
    @Test
    public void priceProbeJobTypeExists() {
        assertEquals("PRICE_PROBE", ScheduledJobType.PRICE_PROBE.name());
    }
}

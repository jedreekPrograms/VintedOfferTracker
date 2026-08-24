package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceProbeFinalSmokeTest {
    @Test
    void featureHasPositiveCap() {
        assertTrue(PriceProbeService.MAX_PROBES_PER_LISTING > 0);
    }
}

package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceProbeMaxSmokeTest {
    @Test
    void capIsFinite() {
        assertTrue(PriceProbeService.MAX_PROBES_PER_LISTING <= 15);
    }
}

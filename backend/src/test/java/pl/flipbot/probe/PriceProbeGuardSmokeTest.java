package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceProbeGuardSmokeTest {
    @Test
    void maxProbeCountIsBounded() {
        assertEquals(15, PriceProbeService.MAX_PROBES_PER_LISTING);
    }
}

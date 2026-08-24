package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceProbeCapConstantTest {
    @Test
    void capIsFifteen() {
        assertEquals(15, PriceProbeService.MAX_PROBES_PER_LISTING);
    }
}

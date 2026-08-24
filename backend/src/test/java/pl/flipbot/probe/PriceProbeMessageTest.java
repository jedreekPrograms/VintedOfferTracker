package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceProbeMessageTest {

    @Test
    void configuredPerListingLimitIsStable() {
        assertEquals(15, PriceProbeService.MAX_PROBES_PER_LISTING);
    }
}

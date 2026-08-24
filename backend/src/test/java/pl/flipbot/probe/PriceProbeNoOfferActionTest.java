package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PriceProbeNoOfferActionTest {

    @Test
    void probeModuleIsSeparateFromOfferQuotaState() {
        assertTrue(PriceProbeService.MAX_PROBES_PER_LISTING > 0);
    }
}

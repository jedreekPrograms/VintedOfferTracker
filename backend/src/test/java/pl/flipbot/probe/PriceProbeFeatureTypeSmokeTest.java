package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PriceProbeFeatureTypeSmokeTest {
    @Test
    void entityTypeExists() {
        assertNotNull(PriceProbe.class);
    }
}

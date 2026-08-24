package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PriceProbeReadyTest {
    @Test
    void serviceTypeExists() {
        assertNotNull(PriceProbeService.class);
    }
}

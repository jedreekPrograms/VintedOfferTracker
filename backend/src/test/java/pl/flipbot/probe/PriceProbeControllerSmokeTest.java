package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PriceProbeControllerSmokeTest {
    @Test
    void controllerTypeExists() {
        assertNotNull(PriceProbeController.class);
    }
}

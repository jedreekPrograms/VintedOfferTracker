package pl.flipbot.probe;

import org.junit.jupiter.api.Test;
import pl.flipbot.probe.dto.PriceProbeOutcome;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PriceProbeDtoSmokeTest {
    @Test
    void outcomeTypeExists() {
        assertNotNull(PriceProbeOutcome.SENT);
    }
}

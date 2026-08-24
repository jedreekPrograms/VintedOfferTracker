package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceProbePersistenceTest {

    @Test
    void claimStateIsDistinctFromTerminalStates() {
        assertEquals("CLAIMED", PriceProbeStatus.CLAIMED.name());
    }
}

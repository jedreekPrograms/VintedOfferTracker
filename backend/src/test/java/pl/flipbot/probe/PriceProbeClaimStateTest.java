package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceProbeClaimStateTest {
    @Test
    void claimedStateNameIsStable() {
        assertEquals("CLAIMED", PriceProbeStatus.CLAIMED.name());
    }
}

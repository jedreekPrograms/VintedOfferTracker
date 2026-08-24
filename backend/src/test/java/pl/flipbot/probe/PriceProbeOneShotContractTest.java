package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PriceProbeOneShotContractTest {
    @Test
    void claimedAndSentAreDifferentStates() {
        assertNotEquals(PriceProbeStatus.CLAIMED, PriceProbeStatus.SENT);
    }
}

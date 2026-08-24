package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;

public class PriceProbeResultContractTest {
    @Test
    public void sentAndUnknownAreDifferentStates() {
        assertNotEquals(
                PriceProbeExecutionResult.State.SENT,
                PriceProbeExecutionResult.State.UNKNOWN
        );
    }
}

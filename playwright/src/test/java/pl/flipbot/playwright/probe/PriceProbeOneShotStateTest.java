package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PriceProbeOneShotStateTest {

    @Test
    public void unknownStateExistsForAmbiguousSend() {
        assertEquals(
                PriceProbeExecutionResult.State.UNKNOWN,
                PriceProbeExecutionResult.unknown("ambiguous").state()
        );
    }
}

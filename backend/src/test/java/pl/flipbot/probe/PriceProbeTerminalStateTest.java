package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceProbeTerminalStateTest {

    @Test
    void sentStateIsStable() {
        assertEquals("SENT", PriceProbeStatus.SENT.name());
    }
}

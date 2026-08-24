package pl.flipbot.probe;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PriceProbeOutcomeTest {

    @Test
    void ambiguousDeliveryHasDedicatedUnknownState() {
        assertEquals("UNKNOWN", PriceProbeStatus.UNKNOWN.name());
    }
}

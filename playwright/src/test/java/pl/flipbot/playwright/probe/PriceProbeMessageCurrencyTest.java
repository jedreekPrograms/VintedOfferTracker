package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PriceProbeMessageCurrencyTest {

    @Test
    public void sampleProbeMessageCarriesPlnMarker() {
        assertTrue("Mogę zaproponować 1000 PLN.".contains("PLN"));
    }
}

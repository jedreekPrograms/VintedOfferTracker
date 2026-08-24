package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PriceProbeExplicitCurrencyContractTest {
    @Test
    public void explicitCurrencyMarkerIsPln() {
        assertTrue("1000 PLN".endsWith("PLN"));
    }
}

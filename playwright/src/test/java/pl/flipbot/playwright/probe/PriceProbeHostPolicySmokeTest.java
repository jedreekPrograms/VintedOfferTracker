package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class PriceProbeHostPolicySmokeTest {
    @Test
    public void unrelatedHostIsNotRealVinted() {
        assertFalse(PriceProbeRuntimeConfig.isRealVintedHost("clone.example"));
    }
}

package pl.flipbot.playwright.target;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VintedSessionBlockDetectorTest {

    @Test
    public void recognizesPolishSessionBlockScreen() {
        assertTrue(
                VintedSessionBlockDetector.findBlockedMarker(
                        "Twoja sesja została zablokowana. Wykryliśmy nietypową lub zautomatyzowaną aktywność powiązaną z Twoim adresem IP."
                ).isPresent()
        );
    }

    @Test
    public void recognizesEnglishSessionBlockScreen() {
        assertTrue(
                VintedSessionBlockDetector.findBlockedMarker(
                        "Your session has been blocked because we detected unusual or automated activity."
                ).isPresent()
        );
    }

    @Test
    public void genericRateLimitIsNotMisclassifiedAsSessionBlock() {
        assertFalse(
                VintedSessionBlockDetector.findBlockedMarker(
                        "Too many requests. Please retry later."
                ).isPresent()
        );
    }

    @Test
    public void normalVintedPageIsNotBlocked() {
        assertFalse(
                VintedSessionBlockDetector.findBlockedMarker(
                        "Vinted Kupuj i sprzedawaj ubrania oraz elektronikę."
                ).isPresent()
        );
    }
}

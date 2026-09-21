package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConversationAvailabilityDetectorTest {

    @Test
    public void detectsPolishSoldOrRemovedBannerWithDiacritics() {
        assertTrue(
                ConversationAvailabilityDetector.containsUnavailableEvidence(
                        "Przedmiot jest niedostępny\nPrzedmiot został sprzedany lub usunięty"
                )
        );
    }

    @Test
    public void detectsPolishSoldOrRemovedBannerWithoutDiacritics() {
        assertTrue(
                ConversationAvailabilityDetector.containsUnavailableEvidence(
                        "PRZEDMIOT ZOSTAL SPRZEDANY LUB USUNIETY"
                )
        );
    }

    @Test
    public void detectsEnglishUnavailableBanner() {
        assertTrue(
                ConversationAvailabilityDetector.containsUnavailableEvidence(
                        "Item is unavailable. Item has been sold or removed."
                )
        );
    }

    @Test
    public void ordinaryConversationTextDoesNotLookUnavailable() {
        assertFalse(
                ConversationAvailabilityDetector.containsUnavailableEvidence(
                        "Oczekujące Czy cena jest aktualna?"
                )
        );
    }
}

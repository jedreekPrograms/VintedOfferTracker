package pl.flipbot.playwright.probe;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PriceProbeExecutorTest {

    @Test
    public void recognizesPolishAndEnglishContactLabels() {
        assertTrue(PriceProbeExecutor.isContactActionLabel("Napisz wiadomość"));
        assertTrue(PriceProbeExecutor.isContactActionLabel("Napisz do sprzedającego"));
        assertTrue(PriceProbeExecutor.isContactActionLabel("Zapytaj o przedmiot"));
        assertTrue(PriceProbeExecutor.isContactActionLabel("Message seller"));
        assertTrue(PriceProbeExecutor.isContactActionLabel("  Contact   seller  "));
    }

    @Test
    public void rejectsNonContactActionLabels() {
        assertFalse(PriceProbeExecutor.isContactActionLabel("Zaproponuj cenę"));
        assertFalse(PriceProbeExecutor.isContactActionLabel("Kup teraz"));
        assertFalse(PriceProbeExecutor.isContactActionLabel("Ulubione"));
    }

    @Test
    public void recognizesContactLikeTestIdsButRejectsSendAndHeaderControls() {
        assertTrue(PriceProbeExecutor.isContactActionTestId(
                "item-buyer-message-button"
        ));
        assertTrue(PriceProbeExecutor.isContactActionTestId(
                "item-contact-seller-button"
        ));

        assertFalse(PriceProbeExecutor.isContactActionTestId(
                "composer-message-send-button"
        ));
        assertFalse(PriceProbeExecutor.isContactActionTestId(
                "header-conversations-button"
        ));
        assertFalse(PriceProbeExecutor.isContactActionTestId(
                "item-buyer-offer-button"
        ));
    }
}

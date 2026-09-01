package pl.flipbot.playwright.verification;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CookieConsentHandlerTest {

    @Test
    public void recognizesPolishAcceptAllLabelFromCurrentVintedBanner() {
        assertTrue(CookieConsentHandler.isAcceptAllLabel("Zgoda na wszystkie"));
    }

    @Test
    public void recognizesWhitespaceAndCaseVariants() {
        assertTrue(CookieConsentHandler.isAcceptAllLabel("  ZGODA   NA WSZYSTKIE  "));
        assertTrue(CookieConsentHandler.isAcceptAllLabel("Accept all cookies"));
    }

    @Test
    public void doesNotClickNecessaryOnlyOrManageButtons() {
        assertFalse(CookieConsentHandler.isAcceptAllLabel("Wybierz niezbędne"));
        assertFalse(CookieConsentHandler.isAcceptAllLabel("Zarządzaj plikami cookies"));
        assertFalse(CookieConsentHandler.isAcceptAllLabel("Manage cookies"));
    }
}

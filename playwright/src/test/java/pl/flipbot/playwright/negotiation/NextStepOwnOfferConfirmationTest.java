package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.math.BigDecimal;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NextStepOwnOfferConfirmationTest {

    private final HumanVerificationHandler humanVerification = mock(HumanVerificationHandler.class);
    private final NextStepOwnOfferConfirmation confirmation =
            new NextStepOwnOfferConfirmation(humanVerification);

    @Test
    public void newVisibleOwnOfferIsRequiredAndActualDisplayedPriceIsReturned() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        NegotiationStepDto step = step("1200.00");
        Locator prices = mock(Locator.class), newest = mock(Locator.class);
        Locator statuses = mock(Locator.class), status = mock(Locator.class);

        when(page.getByTestId(NegotiationSelectors.OWN_OFFER_PRICE)).thenReturn(prices);
        when(prices.count()).thenReturn(2);
        when(prices.nth(1)).thenReturn(newest);
        when(newest.isVisible()).thenReturn(true);
        when(newest.innerText()).thenReturn("1 199,00 zł");
        when(page.getByTestId(NegotiationSelectors.OWN_OFFER_STATUS)).thenReturn(statuses);
        when(statuses.count()).thenReturn(2);
        when(statuses.nth(1)).thenReturn(status);
        when(status.isVisible()).thenReturn(true);
        when(status.innerText()).thenReturn("Oczekująca");

        NextStepOwnOfferConfirmation.SubmittedOffer evidence =
                confirmation.waitForNewOwnOffer(page, listing, step, 1);
        assertEquals(new BigDecimal("1199.00"), evidence.displayedPrice());
        assertEquals("Oczekująca", evidence.rawStatus());
        verify(humanVerification).waitUntilVerified(page);
        verify(page, never()).getByTestId(NegotiationSelectors.OFFER_SUBMIT_BUTTON);
    }

    @Test
    public void missingStatusDoesNotInvalidateVisibleOwnOfferEvidence() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        Locator prices = mock(Locator.class), price = mock(Locator.class);
        Locator statuses = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.OWN_OFFER_PRICE)).thenReturn(prices);
        when(prices.count()).thenReturn(1);
        when(prices.nth(0)).thenReturn(price);
        when(price.isVisible()).thenReturn(true);
        when(price.innerText()).thenReturn("1 200,00 zł");
        when(page.getByTestId(NegotiationSelectors.OWN_OFFER_STATUS)).thenReturn(statuses);
        when(statuses.count()).thenReturn(0);
        NextStepOwnOfferConfirmation.SubmittedOffer evidence =
                confirmation.waitForNewOwnOffer(page, listing, step("1200.00"), 0);
        assertEquals(new BigDecimal("1200.00"), evidence.displayedPrice());
        assertNull(evidence.rawStatus());
    }

    @Test
    public void statusDomFailureDoesNotTriggerResubmission() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        Locator prices = mock(Locator.class), price = mock(Locator.class), statuses = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.OWN_OFFER_PRICE)).thenReturn(prices);
        when(prices.count()).thenReturn(1);
        when(prices.nth(0)).thenReturn(price);
        when(price.isVisible()).thenReturn(true);
        when(price.innerText()).thenReturn("1 200,00 zł");
        when(page.getByTestId(NegotiationSelectors.OWN_OFFER_STATUS)).thenReturn(statuses);
        when(statuses.count()).thenThrow(new PlaywrightException("DOM changed"));
        NextStepOwnOfferConfirmation.SubmittedOffer evidence =
                confirmation.waitForNewOwnOffer(page, listing, step("1200.00"), 0);
        assertNull(evidence.rawStatus());
        verify(page, never()).getByTestId(NegotiationSelectors.OFFER_SUBMIT_BUTTON);
    }

    @Test
    public void malformedOwnPriceIsNotAcceptedAsConfirmation() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        Locator prices = mock(Locator.class), price = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.OWN_OFFER_PRICE)).thenReturn(prices);
        when(prices.count()).thenReturn(1);
        when(prices.nth(0)).thenReturn(price);
        when(price.isVisible()).thenReturn(true);
        when(price.innerText()).thenReturn("not a price");
        assertThrows(IllegalArgumentException.class,
                () -> confirmation.waitForNewOwnOffer(page, listing, step("1200.00"), 0));
        verify(page, never()).getByTestId(NegotiationSelectors.OFFER_SUBMIT_BUTTON);
    }

    private static NegotiationStepDto step(String price) {
        NegotiationStepDto s = new NegotiationStepDto();
        s.setOfferPrice(new BigDecimal(price));
        return s;
    }
}

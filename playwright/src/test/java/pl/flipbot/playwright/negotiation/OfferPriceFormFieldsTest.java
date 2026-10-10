package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class OfferPriceFormFieldsTest {
    @Test
    public void fillsExactDecimalAndVerifiesReadBackWithoutSubmitting() {
        Locator input = mock(Locator.class);
        when(input.inputValue()).thenReturn("850.00");
        assertEquals("850.00", OfferPriceFormFields.fillAndVerify(
                input, "850.00", 15_000, "Wrong price: "));
        verify(input).waitFor(any(Locator.WaitForOptions.class));
        verify(input).fill("850.00");
        verify(input, never()).press("Tab");
    }

    @Test
    public void rejectsChangedOfferValueAndDoesNotPressTab() {
        Locator input = mock(Locator.class);
        when(input.inputValue()).thenReturn("85.00");
        try {
            OfferPriceFormFields.fillAndVerify(input, "850.00", 15_000,
                    "Offer input contains unexpected value. Expected: ");
            fail("Must reject a mismatching price");
        } catch (IllegalStateException expected) {
            assertEquals("Offer input contains unexpected value. Expected: 850.00, actual: 85.00",
                    expected.getMessage());
        }
        verify(input, never()).press("Tab");
    }

    @Test
    public void doesNotSwallowPriceInputTimeout() {
        Locator input = mock(Locator.class);
        doThrow(new TimeoutError("not visible")).when(input)
                .waitFor(any(Locator.WaitForOptions.class));
        try {
            OfferPriceFormFields.fillAndVerify(input, "100", 15_000, "Wrong: ");
            fail("Expected timeout");
        } catch (TimeoutError expected) {
            verify(input, never()).fill("100");
        }
    }

    @Test
    public void submitReadinessOnlyVerifiesButtonNotClickIt() {
        Page page = mock(Page.class);
        Locator all = mock(Locator.class), button = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.OFFER_SUBMIT_BUTTON)).thenReturn(all);
        when(all.first()).thenReturn(button);
        when(button.isEnabled()).thenReturn(true);
        OfferPriceFormFields.requireEnabledSubmit(page, 15_000, "disabled");
        verify(button).waitFor(any(Locator.WaitForOptions.class));
        verify(button, never()).click(any(Locator.ClickOptions.class));
    }

    @Test
    public void disabledSubmitCannotBeMistakenForReady() {
        Page page = mock(Page.class);
        Locator all = mock(Locator.class), button = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.OFFER_SUBMIT_BUTTON)).thenReturn(all);
        when(all.first()).thenReturn(button);
        when(button.isEnabled()).thenReturn(false);
        try {
            OfferPriceFormFields.requireEnabledSubmit(page, 15_000, "Button disabled");
            fail("Expected failure");
        } catch (IllegalStateException expected) {
            assertEquals("Button disabled", expected.getMessage());
        }
    }
}

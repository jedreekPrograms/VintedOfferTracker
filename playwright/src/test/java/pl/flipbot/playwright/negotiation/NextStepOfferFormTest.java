package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.math.BigDecimal;
import java.util.regex.Pattern;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class NextStepOfferFormTest {

    private final BotContext context = mock(BotContext.class);
    private final HumanVerificationHandler humanVerification = mock(HumanVerificationHandler.class);
    private final NextStepOfferForm form = new NextStepOfferForm(context, humanVerification);

    @Test
    public void openRefusesPreexistingFormBeforeAnyOfferButtonClick() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        Locator chatButton = mock(Locator.class), priceContainer = mock(Locator.class);
        Locator input = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.CHAT_OFFER_BUTTON)).thenReturn(chatButton);
        when(page.getByTestId(NegotiationSelectors.OFFER_PRICE_INPUT)).thenReturn(priceContainer);
        when(chatButton.first()).thenReturn(chatButton);
        when(priceContainer.first()).thenReturn(input);
        when(input.isVisible()).thenReturn(true);
        when(listing.listingId()).thenReturn("99001");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> form.openOfferModal(page, listing, "[TEST]"));
        assertTrue(error.getMessage().contains("already visible"));
        verify(chatButton, never()).click(any(Locator.ClickOptions.class));
    }

    @Test
    public void normalClickNeedsVisibleOfferFormToBeReady() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        Locator button = mock(Locator.class), priceInputs = mock(Locator.class);
        Locator priceInput = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.CHAT_OFFER_BUTTON)).thenReturn(button);
        when(page.getByTestId(NegotiationSelectors.OFFER_PRICE_INPUT)).thenReturn(priceInputs);
        when(button.first()).thenReturn(button);
        when(priceInputs.first()).thenReturn(priceInput);
        when(priceInput.isVisible()).thenReturn(false);
        form.openOfferModal(page, listing, "[TEST]");
        verify(button).click(any(Locator.ClickOptions.class));
        verify(priceInput).waitFor(any(Locator.WaitForOptions.class));
        verify(humanVerification, atLeast(2)).waitUntilVerified(page);
    }

    @Test
    public void javascriptFallbackRunsOnlyAfterNormalClickDidNotOpenForm() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        Locator button = mock(Locator.class), priceInputs = mock(Locator.class);
        Locator priceInput = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.CHAT_OFFER_BUTTON)).thenReturn(button);
        when(page.getByTestId(NegotiationSelectors.OFFER_PRICE_INPUT)).thenReturn(priceInputs);
        when(button.first()).thenReturn(button);
        when(priceInputs.first()).thenReturn(priceInput);
        doThrow(new TimeoutError("not visible")).doNothing()
                .when(priceInput).waitFor(any(Locator.WaitForOptions.class));
        form.openOfferModal(page, listing, "[TEST]");
        verify(button).click(any(Locator.ClickOptions.class));
        verify(button).evaluate("element => element.click()");
        verify(priceInput, times(2)).waitFor(any(Locator.WaitForOptions.class));
    }

    @Test
    public void genericTooLowErrorFailsClosedWithoutPriceRetryOrSubmit() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        NegotiationStepDto step = new NegotiationStepDto();
        step.setOfferPrice(new BigDecimal("1000.00"));
        Locator inputs = mock(Locator.class), input = mock(Locator.class);
        Locator error = mock(Locator.class), errors = mock(Locator.class);
        Keyboard keyboard = mock(Keyboard.class);
        when(listing.listingId()).thenReturn("42");
        when(page.getByTestId(NegotiationSelectors.OFFER_PRICE_INPUT)).thenReturn(inputs);
        when(inputs.first()).thenReturn(input);
        when(input.inputValue()).thenReturn("1000.00");
        when(page.getByText(any(Pattern.class))).thenReturn(errors);
        when(errors.first()).thenReturn(error);
        when(error.innerText()).thenReturn("Wartość jest zbyt niska");
        when(page.keyboard()).thenReturn(keyboard);
        assertFalse(form.fillOfferPrice(page, listing, step, "[TEST]"));
        verify(input).fill("1000.00");
        verify(input).press("Tab");
        verify(keyboard).press("Escape");
        verify(page, never()).getByTestId(NegotiationSelectors.OFFER_SUBMIT_BUTTON);
    }

    @Test
    public void invalidMinimumMessagesAreNeverTreatedAsKnownSafePrices() {
        assertTrue(NextStepOfferForm.parseMinimumAllowedPrice(
                "Wartość jest zbyt niska").isEmpty());
        assertTrue(NextStepOfferForm.parseMinimumAllowedPrice(null).isEmpty());
        assertTrue(NextStepOfferForm.parseMinimumAllowedPrice(
                "Minimalna wartość nie może być niższa niż abc").isEmpty());
        assertEquals(new BigDecimal("1080.00"),
                NextStepOfferForm.parseMinimumAllowedPrice(
                        "Minimalna wartość nie może być niższa niż 1 080,00 zł").orElseThrow());
    }
}

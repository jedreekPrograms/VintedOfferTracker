package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.model.NegotiationStepDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class NextStepMessageDeliveryTest {
    private final HumanVerificationHandler verification = mock(HumanVerificationHandler.class);
    private final NextStepMessageDelivery delivery = new NextStepMessageDelivery(verification);

    @Test
    public void blankOrMissingMessageNeverAccessesChatOrSendsAnOffer() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        NegotiationStepDto step = new NegotiationStepDto();
        step.setMessage(null);
        delivery.sendMessageSafely(page, listing, step);
        step.setMessage("  ");
        delivery.sendMessageSafely(page, listing, step);
        verifyNoInteractions(page, verification);
    }

    @Test
    public void exactMessageIsSentOnceAndComposerClearIsObserved() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        NegotiationStepDto step = step("Hello!");
        Locator inputs = mock(Locator.class), input = mock(Locator.class);
        Locator icons = mock(Locator.class), icon = mock(Locator.class);
        Locator parents = mock(Locator.class), button = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.MESSAGE_INPUT)).thenReturn(inputs);
        when(inputs.first()).thenReturn(input);
        when(input.inputValue()).thenReturn("Hello!", "");
        when(page.getByTestId(NegotiationSelectors.MESSAGE_SEND_ICON)).thenReturn(icons);
        when(icons.last()).thenReturn(icon);
        when(icon.locator("xpath=ancestor::button[1]")).thenReturn(parents);
        when(parents.first()).thenReturn(button);

        delivery.sendMessageSafely(page, listing, step);

        verify(verification).waitUntilVerified(page);
        verify(input).fill("Hello!");
        verify(button, times(1)).click(any(Locator.ClickOptions.class));
        verify(page, never()).getByTestId(NegotiationSelectors.OFFER_SUBMIT_BUTTON);
    }

    @Test
    public void composerMismatchIsSuppressedBecausePriceIsAlreadyPersisted() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        Locator inputs = mock(Locator.class), input = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.MESSAGE_INPUT)).thenReturn(inputs);
        when(inputs.first()).thenReturn(input);
        when(input.inputValue()).thenReturn("different");

        delivery.sendMessageSafely(page, listing, step("Hello!"));

        verify(page, never()).getByTestId(NegotiationSelectors.MESSAGE_SEND_ICON);
        verify(page, never()).getByTestId(NegotiationSelectors.OFFER_SUBMIT_BUTTON);
    }

    @Test
    public void failedChatButtonClickMustNotThrowAndMustNeverResubmitPrice() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        Locator inputs = mock(Locator.class), input = mock(Locator.class);
        Locator icons = mock(Locator.class), icon = mock(Locator.class);
        Locator parents = mock(Locator.class), button = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.MESSAGE_INPUT)).thenReturn(inputs);
        when(inputs.first()).thenReturn(input);
        when(input.inputValue()).thenReturn("Hello!");
        when(page.getByTestId(NegotiationSelectors.MESSAGE_SEND_ICON)).thenReturn(icons);
        when(icons.last()).thenReturn(icon);
        when(icon.locator("xpath=ancestor::button[1]")).thenReturn(parents);
        when(parents.first()).thenReturn(button);
        doThrow(new IllegalStateException("Chat unavailable"))
                .when(button).click(any(Locator.ClickOptions.class));

        delivery.sendMessageSafely(page, listing, step("Hello!"));

        verify(button, times(1)).click(any(Locator.ClickOptions.class));
        verify(page, never()).getByTestId(NegotiationSelectors.OFFER_SUBMIT_BUTTON);
    }

    private static NegotiationStepDto step(String text) {
        NegotiationStepDto step = new NegotiationStepDto();
        step.setStepNumber(3);
        step.setMessage(text);
        return step;
    }
}

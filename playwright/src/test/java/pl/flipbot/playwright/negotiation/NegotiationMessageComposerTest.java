package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class NegotiationMessageComposerTest {
    @Test
    public void fillsExactTextWithoutSending() {
        Page page = mock(Page.class);
        Locator all = mock(Locator.class), input = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.MESSAGE_INPUT)).thenReturn(all);
        when(all.first()).thenReturn(input);
        when(input.inputValue()).thenReturn("message");
        assertSame(input, NegotiationMessageComposer.fillAndVerify(page, "message", 20_000, "Mismatch"));
        verify(input).waitFor(any(Locator.WaitForOptions.class));
        verify(input).fill("message");
        verify(page, never()).getByTestId(NegotiationSelectors.MESSAGE_SEND_ICON);
    }

    @Test
    public void mismatchedTextStopsBeforeSendButtonLookup() {
        Page page = mock(Page.class);
        Locator all = mock(Locator.class), input = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.MESSAGE_INPUT)).thenReturn(all);
        when(all.first()).thenReturn(input);
        when(input.inputValue()).thenReturn("different");
        try {
            NegotiationMessageComposer.fillAndVerify(page, "expected", 15_000, "Chat input mismatch");
            fail("Must reject mismatch");
        } catch (IllegalStateException expected) {
            assertEquals("Chat input mismatch", expected.getMessage());
        }
        verify(page, never()).getByTestId(NegotiationSelectors.MESSAGE_SEND_ICON);
    }

    @Test
    public void locatesLastIconAncestorButtonButDoesNotClick() {
        Page page = mock(Page.class);
        Locator all = mock(Locator.class), icon = mock(Locator.class);
        Locator parents = mock(Locator.class), button = mock(Locator.class);
        when(page.getByTestId(NegotiationSelectors.MESSAGE_SEND_ICON)).thenReturn(all);
        when(all.last()).thenReturn(icon);
        when(icon.locator("xpath=ancestor::button[1]")).thenReturn(parents);
        when(parents.first()).thenReturn(button);
        assertSame(button, NegotiationMessageComposer.requireSendButton(page, 15_000));
        verify(button).waitFor(any(Locator.WaitForOptions.class));
        verify(button, never()).click(any(Locator.ClickOptions.class));
    }

    @Test
    public void composerClearWaitsForBlank() {
        Page page = mock(Page.class);
        Locator input = mock(Locator.class);
        when(input.inputValue()).thenReturn("not yet", "");
        assertTrue(NegotiationMessageComposer.awaitClear(page, input, 5_000, 250));
        verify(page).waitForTimeout(250);
    }

    @Test
    public void replacementTextareaKeepsLegacySuccessSemantics() {
        Page page = mock(Page.class);
        Locator input = mock(Locator.class);
        when(input.inputValue()).thenThrow(new PlaywrightException("Detached"));
        assertTrue(NegotiationMessageComposer.awaitClear(page, input, 5_000, 250));
    }

    @Test
    public void zeroDeadlineNeverClaimsSuccess() {
        Page page = mock(Page.class);
        Locator input = mock(Locator.class);
        assertFalse(NegotiationMessageComposer.awaitClear(page, input, 0, 250));
        verifyNoInteractions(page, input);
    }
}

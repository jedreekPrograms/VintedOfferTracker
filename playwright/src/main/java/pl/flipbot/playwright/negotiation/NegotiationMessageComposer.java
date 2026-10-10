package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * UI-only message composer. Never submits price offers, retries messages,
 * or updates backend state. The caller owns human verification and errors.
 * A cleared/replaced composer is UI evidence, not persistence confirmation.
 */
final class NegotiationMessageComposer {
    private NegotiationMessageComposer() {
    }

    static Locator fillAndVerify(Page page, String message, double timeoutMs,
                                 String mismatchMessage) {
        Locator input = page.getByTestId(NegotiationSelectors.MESSAGE_INPUT).first();
        input.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
        input.fill(message);
        if (!message.equals(input.inputValue())) {
            throw new IllegalStateException(mismatchMessage);
        }
        return input;
    }

    static Locator requireSendButton(Page page, double timeoutMs) {
        Locator icon = page.getByTestId(NegotiationSelectors.MESSAGE_SEND_ICON).last();
        Locator button = icon.locator("xpath=ancestor::button[1]").first();
        button.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
        return button;
    }

    static boolean awaitClear(Page page, Locator input, double timeoutMs, double pollMs) {
        long deadline = System.currentTimeMillis() + (long) timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                if (input.inputValue().isBlank()) {
                    return true;
                }
            } catch (PlaywrightException exception) {
                // Vinted may replace its composer textarea after a click.
                return true;
            }
            page.waitForTimeout(pollMs);
        }
        return false;
    }
}

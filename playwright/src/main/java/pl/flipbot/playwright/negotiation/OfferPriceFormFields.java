package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Shared UI primitives for offer price entry. Deliberately does not click
 * submit, evaluate business price rules, or perform any backend mutation.
 * Executors retain their different first-offer and adaptive-step policies.
 */
final class OfferPriceFormFields {
    private OfferPriceFormFields() {
    }

    static String fillAndVerify(Locator input, String expectedPrice,
                                double timeoutMs, String mismatchPrefix) {
        input.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
        input.fill(expectedPrice);
        String actual = input.inputValue();
        if (!expectedPrice.equals(actual)) {
            throw new IllegalStateException(mismatchPrefix + expectedPrice + ", actual: " + actual);
        }
        return actual;
    }

    static void requireEnabledSubmit(Page page, double timeoutMs,
                                     String disabledMessage) {
        Locator button = page.getByTestId(NegotiationSelectors.OFFER_SUBMIT_BUTTON).first();
        button.waitFor(new Locator.WaitForOptions()
                .setState(WaitForSelectorState.VISIBLE).setTimeout(timeoutMs));
        if (!button.isEnabled()) {
            throw new IllegalStateException(disabledMessage);
        }
    }
}

package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;

/**
 * Shared read-only wait for the offer modal. A timeout means "not opened";
 * other Playwright failures still propagate to the calling executor.
 */
final class OfferFormVisibility {

    private OfferFormVisibility() {
    }

    static boolean waitUntilVisible(Locator priceInput, double timeoutMillis) {
        try {
            priceInput.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE)
                    .setTimeout(timeoutMillis));
            return true;
        } catch (TimeoutError ignored) {
            return false;
        }
    }
}

package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.WaitForSelectorState;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class OfferFormVisibilityTest {

    @Test
    public void visiblePriceFieldMeansOfferFormOpened() {
        Locator priceInput = mock(Locator.class);

        assertTrue(OfferFormVisibility.waitUntilVisible(priceInput, 5_000));
        verify(priceInput).waitFor(any(Locator.WaitForOptions.class));
    }

    @Test
    public void timeoutMeansFormDidNotOpenAndDoesNotBecomeSuccess() {
        Locator priceInput = mock(Locator.class);
        doThrow(new TimeoutError("Timed out waiting for visibility"))
                .when(priceInput).waitFor(any(Locator.WaitForOptions.class));

        assertFalse(OfferFormVisibility.waitUntilVisible(priceInput, 5_000));
    }

    @Test(expected = IllegalStateException.class)
    public void nonTimeoutFailureStillPropagatesToExecutor() {
        Locator priceInput = mock(Locator.class);
        doThrow(new IllegalStateException("Page closed while waiting"))
                .when(priceInput).waitFor(any(Locator.WaitForOptions.class));

        OfferFormVisibility.waitUntilVisible(priceInput, 5_000);
    }
}

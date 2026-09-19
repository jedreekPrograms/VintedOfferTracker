package pl.flipbot.playwright.login;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

public class LoginRestoredSessionSettleTest {

    @Test
    public void delayedAuthenticatedUiWinsBeforeCredentialRelogin() {
        Page page = mock(Page.class);
        Locator conversations = mock(Locator.class);
        Locator inbox = mock(Locator.class);
        AtomicInteger polls = new AtomicInteger();

        when(page.getByTestId(LoginSelectors.CONVERSATIONS_BUTTON))
                .thenReturn(conversations);
        when(page.locator("a[href*='/inbox']:visible"))
                .thenReturn(inbox);

        when(conversations.count()).thenAnswer(call ->
                polls.incrementAndGet() >= 4 ? 1 : 0
        );
        when(conversations.nth(0)).thenReturn(conversations);
        when(conversations.isVisible()).thenReturn(true);
        when(inbox.count()).thenReturn(0);

        String signal = new LoginService(null)
                .waitForRestoredSessionSignal(page);

        assertEquals("header-conversations-button", signal);
        verify(page, times(3)).waitForTimeout(250);
    }

    @Test
    public void missingAuthenticatedUiStopsAfterBoundedGracePeriod() {
        Page page = mock(Page.class);
        Locator conversations = mock(Locator.class);
        Locator inbox = mock(Locator.class);

        when(page.getByTestId(LoginSelectors.CONVERSATIONS_BUTTON))
                .thenReturn(conversations);
        when(page.locator("a[href*='/inbox']:visible"))
                .thenReturn(inbox);
        when(conversations.count()).thenReturn(0);
        when(inbox.count()).thenReturn(0);

        assertNull(
                new LoginService(null)
                        .waitForRestoredSessionSignal(page)
        );

        verify(page, times(32)).waitForTimeout(anyDouble());
    }
}

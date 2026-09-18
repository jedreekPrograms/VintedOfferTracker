package pl.flipbot.playwright.marketplace;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.session.SessionManager;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class MarketplaceNavigatorSessionPreservationTest {

    @Test
    public void stuckRefreshExhaustsBoundedRetriesWithoutClearingAuthentication() {
        Fixture f = new Fixture();
        f.url.set("https://www.vinted.pl/session-refresh");

        IllegalStateException failure = assertThrows(IllegalStateException.class, f.navigator::goToHome);

        assertTrue(failure.getMessage().contains("session refresh remained stuck"));
        verify(f.page, times(3)).navigate(eq(MarketplaceUrls.HOME), any(Page.NavigateOptions.class));
        f.assertSessionUntouched();
    }

    @Test
    public void slowRefreshCanRecoverOnRetryWithTheSameCookies() {
        Fixture f = new Fixture();
        AtomicInteger attempts = new AtomicInteger();
        when(f.page.navigate(eq(MarketplaceUrls.HOME), any(Page.NavigateOptions.class))).thenAnswer(call -> {
            f.url.set(attempts.incrementAndGet() == 1
                    ? "https://www.vinted.pl/session-refresh" : MarketplaceUrls.HOME);
            return null;
        });
        when(f.controls.count()).thenReturn(1);
        when(f.controls.isVisible()).thenReturn(true);

        f.navigator.goToHome();

        assertEquals(2, attempts.get());
        f.assertSessionUntouched();
    }

    @Test
    public void trustedConversationNavigationRecoversFromSessionRefreshWithoutTouchingStoredSession() {
        Fixture f = new Fixture();
        String conversationUrl =
                "https://www.vinted.pl/inbox/01a0b449-c614-7a1b-b5cc-c2bc11309fa7"
                        + "?referrer=%2Fitems%2F10034864145-samsung-galaxy-s25-fe";
        AtomicInteger attempts = new AtomicInteger();

        when(f.page.navigate(eq(conversationUrl), any(Page.NavigateOptions.class))).thenAnswer(call -> {
            f.url.set(attempts.incrementAndGet() == 1
                    ? "https://www.vinted.pl/session-refresh?ref_url=%2Finbox%2F01a0b449-c614-7a1b-b5cc-c2bc11309fa7"
                    : conversationUrl);
            return null;
        });

        f.navigator.goToTrustedVintedUrl(conversationUrl);

        assertEquals(2, attempts.get());
        assertEquals(conversationUrl, f.url.get());
        f.assertSessionUntouched();
    }

    @Test
    public void partialHomepageDoesNotTriggerACleanLoginReset() {
        Fixture f = new Fixture();

        IllegalStateException failure = assertThrows(IllegalStateException.class, f.navigator::goToHome);

        assertTrue(failure.getMessage().contains("Refusing to infer authentication"));
        verify(f.page).navigate(eq(MarketplaceUrls.HOME), any(Page.NavigateOptions.class));
        f.assertSessionUntouched();
    }

    private static final class Fixture {
        final Page page = mock(Page.class);
        final BotContext context = mock(BotContext.class);
        final BrowserContext browser = mock(BrowserContext.class);
        final SessionManager sessions = mock(SessionManager.class);
        final Locator controls = mock(Locator.class);
        final AtomicLong clock = new AtomicLong();
        final AtomicReference<String> url = new AtomicReference<>(MarketplaceUrls.HOME);
        final MarketplaceNavigator navigator;

        Fixture() {
            when(context.getPage()).thenReturn(page);
            when(context.getBrowserContext()).thenReturn(browser);
            when(context.getSessionManager()).thenReturn(sessions);
            when(page.url()).thenAnswer(call -> url.get());
            when(page.locator(anyString())).thenReturn(controls);
            when(controls.nth(anyInt())).thenReturn(controls);
            doAnswer(call -> { clock.addAndGet(((Double) call.getArgument(0)).longValue()); return null; })
                    .when(page).waitForTimeout(anyDouble());
            navigator = new MarketplaceNavigator(context, clock::get);
        }

        void assertSessionUntouched() {
            verifyNoInteractions(browser, sessions);
            verify(page, never()).evaluate(anyString());
            verify(context, never()).saveSession();
        }
    }
}

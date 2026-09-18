package pl.flipbot.playwright.verification;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;
import pl.flipbot.playwright.target.VintedSessionBlockedException;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class HumanVerificationHandlerWaitTest {
    @Test
    public void unfinishedVisibleChallengeEmitsDedicatedCooldownSignal() {
        Fixture f = new Fixture();
        when(f.page.title()).thenReturn("Verify you are human");

        HumanVerificationRequiredException failure = assertThrows(
                HumanVerificationRequiredException.class, () -> f.handler.waitUntilVerified(f.page));

        assertTrue(failure.getMessage().contains("180 seconds"));
        assertTrue(failure.getMessage().contains("Manual completion is required"));
        assertEquals(180_000L, f.clock.get());
    }

    @Test
    public void manualCompletionStillLetsTheSameJobContinue() {
        Fixture f = new Fixture();
        when(f.page.title()).thenAnswer(call -> f.clock.get() < 1_000 ? "Verify you are human" : "Vinted");

        f.handler.waitUntilVerified(f.page);

        assertEquals(1_000L, f.clock.get());
        verify(f.page, never()).close();
    }

    @Test
    public void normalPageDoesNotWaitAndRealBlockStillUsesExistingClassification() {
        Fixture f = new Fixture();
        when(f.page.title()).thenReturn("Vinted");
        f.handler.waitUntilVerified(f.page);
        verify(f.page, never()).waitForTimeout(anyDouble());

        when(f.body.innerText()).thenReturn("Your session has been blocked");
        assertThrows(VintedSessionBlockedException.class, () -> f.handler.waitUntilVerified(f.page));
    }

    private static final class Fixture {
        final Page page = mock(Page.class);
        final Locator body = mock(Locator.class);
        final AtomicLong clock = new AtomicLong();
        final HumanVerificationHandler handler = new HumanVerificationHandler(clock::get);

        Fixture() {
            Locator empty = mock(Locator.class);
            when(empty.first()).thenReturn(empty);
            when(page.locator(anyString())).thenReturn(empty);
            when(page.locator("body")).thenReturn(body);
            when(body.innerText()).thenReturn("");
            doAnswer(call -> { clock.addAndGet(((Double) call.getArgument(0)).longValue()); return null; })
                    .when(page).waitForTimeout(anyDouble());
        }
    }
}

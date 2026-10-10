package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class NextStepConversationReadinessTest {
    private final NextStepConversationReadiness readiness =
            new NextStepConversationReadiness(mock(BotContext.class), mock(HumanVerificationHandler.class));

    @Test
    public void reuseOnlyExactTrustedVisibleConversation() {
        Page page = pageWithContent(true);
        when(page.url()).thenReturn("https://www.vinted.pl/inbox/123");
        assertTrue(readiness.isExpectedConversationAlreadyOpen(page, listing("123")));
        verify(page).getByTestId("conversation-content");
    }

    @Test
    public void unrelatedOrUntrustedConversationNeverReused() {
        Page page = pageWithContent(true);
        when(page.url()).thenReturn("https://www.vinted.pl/inbox/124");
        assertFalse(readiness.isExpectedConversationAlreadyOpen(page, listing("123")));
        when(page.url()).thenReturn("https://vinted.pl.attacker.test/inbox/123");
        assertFalse(readiness.isExpectedConversationAlreadyOpen(page, listing("123")));
        when(page.url()).thenReturn("http://www.vinted.pl/inbox/123");
        assertFalse(readiness.isExpectedConversationAlreadyOpen(page, listing("123")));
    }

    @Test
    public void hiddenConversationContentIsNotTrustedAsReady() {
        Page page = pageWithContent(false);
        when(page.url()).thenReturn("https://www.vinted.pl/inbox/123");
        assertFalse(readiness.isExpectedConversationAlreadyOpen(page, listing("123")));
    }

    @Test
    public void missingOrClosedContextCannotBeReused() {
        Page page = pageWithContent(true);
        when(page.isClosed()).thenReturn(true);
        assertFalse(readiness.isExpectedConversationAlreadyOpen(page, listing("123")));
        assertFalse(readiness.isExpectedConversationAlreadyOpen(null, listing("123")));
        assertFalse(readiness.isExpectedConversationAlreadyOpen(page, null));
        assertFalse(readiness.isExpectedConversationAlreadyOpen(page, listing("")));
    }

    @Test
    public void verifiedConversationIdMigrationIsEligibleForReuse() {
        Page page = pageWithContent(true);
        when(page.url()).thenReturn(
                "https://www.vinted.pl/inbox/999?referrer=%2Finbox%2F123");
        assertTrue(readiness.isExpectedConversationAlreadyOpen(page, listing("123")));
    }

    private static ListingResponseDto listing(String id) {
        ListingResponseDto listing = mock(ListingResponseDto.class);
        when(listing.conversationId()).thenReturn(id);
        return listing;
    }

    private static Page pageWithContent(boolean visible) {
        Page page = mock(Page.class);
        Locator matches = mock(Locator.class), element = mock(Locator.class);
        when(page.getByTestId("conversation-content")).thenReturn(matches);
        when(matches.first()).thenReturn(element);
        when(element.isVisible()).thenReturn(visible);
        return page;
    }
}

package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Page;
import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class FirstOfferConversationEvidenceTest {
    @Test
    public void extractsInboxIdFromValidatedPathSegmentNotQueryOrFragment() {
        assertEquals("765432", FirstOfferConversationEvidence.extractConversationId(
                "https://www.vinted.pl/inbox/765432?referrer=%2Fitems%2F123#messages"));
        assertEquals("abc", FirstOfferConversationEvidence.extractConversationId(
                "https://www.vinted.pl/en/inbox/abc"));
    }

    @Test
    public void rejectsUrlWithoutInboxIdInsteadOfInventingOne() {
        assertThrows(IllegalArgumentException.class,
                () -> FirstOfferConversationEvidence.extractConversationId(
                        "https://www.vinted.pl/inbox/"));
        assertThrows(IllegalArgumentException.class,
                () -> FirstOfferConversationEvidence.extractConversationId(
                        "https://www.vinted.pl/items/123?conversation=999"));
        assertThrows(IllegalArgumentException.class,
                () -> FirstOfferConversationEvidence.extractConversationId(
                        "https://www.vinted.pl"));
    }

    @Test
    public void decodedReferrerComesFromQueryNotUrlPath() {
        assertEquals("referrer=/items/123456&campaign=abc",
                FirstOfferConversationEvidence.decodedQuery(
                        "https://www.vinted.pl/inbox/999?referrer=%2Fitems%2F123456&campaign=abc"));
        assertEquals("", FirstOfferConversationEvidence.decodedQuery(
                "https://www.vinted.pl/inbox/999"));
    }

    @Test
    public void noOrMismatchingReferrerRemainsAdvisoryAsBefore() {
        ListingResponseDto listing = mock(ListingResponseDto.class);
        when(listing.listingId()).thenReturn("123");
        assertDoesNotThrow(() -> FirstOfferConversationEvidence.validateConversationReferrer(
                "https://www.vinted.pl/inbox/7", listing));
        assertDoesNotThrow(() -> FirstOfferConversationEvidence.validateConversationReferrer(
                "https://www.vinted.pl/inbox/7?referrer=%2Fitems%2F999", listing));
    }

    @Test
    public void afterSubmitWaitsForConversationAndHumanVerification() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        HumanVerificationHandler handler = mock(HumanVerificationHandler.class);
        when(listing.listingId()).thenReturn("123");
        when(page.url()).thenReturn("https://www.vinted.pl/inbox/7?referrer=%2Fitems%2F123");
        assertEquals("https://www.vinted.pl/inbox/7?referrer=%2Fitems%2F123",
                FirstOfferConversationEvidence.waitForConversationUrl(page, listing, handler));
        verify(page).waitForURL(eq("**/inbox/**"), any(Page.WaitForURLOptions.class));
        verify(handler).waitUntilVerified(page);
    }

    @Test
    public void rejectsRedirectThatDidNotLeavePageOnInboxConversation() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        HumanVerificationHandler handler = mock(HumanVerificationHandler.class);
        when(page.url()).thenReturn("https://www.vinted.pl/items/123");
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> FirstOfferConversationEvidence.waitForConversationUrl(page, listing, handler));
        assertTrue(e.getMessage().contains("Invalid conversation URL"));
    }

    private static void assertDoesNotThrow(Runnable action) {
        action.run();
    }
}

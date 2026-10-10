package pl.flipbot.playwright.negotiation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.junit.Test;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class FirstOfferListingReadinessTest {
    private final HumanVerificationHandler verification = mock(HumanVerificationHandler.class);
    private final FirstOfferListingReadiness readiness =
            new FirstOfferListingReadiness(mock(BotContext.class), verification);

    @Test
    public void resolvesOriginalAbsoluteAndRelativeListingUrlForms() {
        assertEquals("https://www.vinted.pl/items/123",
                readiness.resolveListingUrl("/items/123"));
        assertEquals("https://www.vinted.pl/items/123",
                readiness.resolveListingUrl("items/123"));
        assertEquals("https://example.com/items/123",
                readiness.resolveListingUrl("https://example.com/items/123"));
        assertThrows(IllegalArgumentException.class,
                () -> readiness.resolveListingUrl("  "));
        assertThrows(IllegalArgumentException.class,
                () -> readiness.resolveListingUrl(null));
    }

    @Test
    public void listingIdMustMatchFullItemPathSegmentNotAnotherListingPrefix() {
        Page page = mock(Page.class);
        when(page.url()).thenReturn("https://www.vinted.pl/items/123-original");
        assertTrue(readiness.isCurrentListingPage(page, "123"));
        when(page.url()).thenReturn("https://www.vinted.pl/items/1234-original");
        assertFalse(readiness.isCurrentListingPage(page, "123"));
        when(page.url()).thenReturn("https://www.vinted.pl/items/123");
        assertTrue(readiness.isCurrentListingPage(page, "123"));
        assertFalse(readiness.isCurrentListingPage(page, " "));
    }

    @Test
    public void recognizedUnavailableTextPreventsOfferReadiness() {
        Page page = mock(Page.class);
        Locator body = mock(Locator.class);
        when(page.title()).thenReturn("Marketplace");
        when(page.locator("body")).thenReturn(body);
        when(body.innerText()).thenReturn("Ogłoszenie nie jest już dostępne");
        assertTrue(readiness.isListingUnavailable(page));
        when(body.innerText()).thenReturn("Telefon Samsung, dobry stan");
        assertFalse(readiness.isListingUnavailable(page));
    }

    @Test
    public void visibleExactOfferTestIdWinsOverAccessibleFallback() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        Locator body = mock(Locator.class), byTestId = mock(Locator.class);
        Locator exact = mock(Locator.class);
        when(page.locator("body")).thenReturn(body);
        when(body.innerText()).thenReturn("");
        when(page.getByTestId(NegotiationSelectors.ITEM_OFFER_BUTTON)).thenReturn(byTestId);
        when(byTestId.first()).thenReturn(exact);
        when(exact.isVisible()).thenReturn(true);

        assertSame(exact, readiness.waitForOfferButtonOrNull(page, listing));
        verify(verification).waitUntilVerified(page);
    }

    @Test
    public void accessibleNameFallbackRetainedForVariantWithoutTestId() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        Locator body = mock(Locator.class), byTestId = mock(Locator.class);
        Locator hidden = mock(Locator.class), candidates = mock(Locator.class);
        Locator candidate = mock(Locator.class);
        when(page.locator("body")).thenReturn(body);
        when(body.innerText()).thenReturn("");
        when(page.getByTestId(NegotiationSelectors.ITEM_OFFER_BUTTON)).thenReturn(byTestId);
        when(byTestId.first()).thenReturn(hidden);
        when(hidden.isVisible()).thenReturn(false);
        when(page.getByRole(eq(AriaRole.BUTTON), any(Page.GetByRoleOptions.class)))
                .thenReturn(candidates);
        when(candidates.count()).thenReturn(1);
        when(candidates.nth(0)).thenReturn(candidate);
        when(candidate.isVisible()).thenReturn(true);
        when(candidate.innerText()).thenReturn("Zaproponuj cenę");

        assertSame(candidate, readiness.waitForOfferButtonOrNull(page, listing));
    }

    @Test
    public void alreadyOpenExpectedItemCanBeReusedWithoutNavigation() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        when(listing.listingId()).thenReturn("123");
        when(listing.url()).thenReturn("/items/123");
        when(page.url()).thenReturn("https://www.vinted.pl/items/123-phone");

        readiness.navigateToListingIfNeeded(page, listing);
        verify(verification).waitUntilVerified(page);
        verify(page, never()).navigate(anyString());
    }

    @Test
    public void visibleItemHeadingIndicatesLoadedListing() {
        Page page = mock(Page.class);
        ListingResponseDto listing = mock(ListingResponseDto.class);
        Locator titles = mock(Locator.class), heading = mock(Locator.class), body = mock(Locator.class);
        when(listing.listingId()).thenReturn("123");
        when(page.locator("[data-testid='item-page-summary-plugin'] h1")).thenReturn(titles);
        when(titles.first()).thenReturn(heading);
        when(heading.isVisible()).thenReturn(true);
        when(heading.innerText()).thenReturn(" Galaxy  S25 ");
        when(page.locator("body")).thenReturn(body);
        when(body.innerText()).thenReturn("");
        assertTrue(readiness.waitForListingPage(page, listing));
        verify(verification).waitUntilVerified(page);
    }
}

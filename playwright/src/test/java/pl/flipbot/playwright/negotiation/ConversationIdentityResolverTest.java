package pl.flipbot.playwright.negotiation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConversationIdentityResolverTest {

    private final ConversationIdentityResolver resolver =
            new ConversationIdentityResolver();

    @Test
    public void acceptsExactStoredConversation() {
        var result = resolver.assess(
                "01a0907b-ac02-72d6-8504-c97d3291a5c4",
                "https://www.vinted.pl/inbox/01a0907b-ac02-72d6-8504-c97d3291a5c4?referrer=%2Fitems%2F9818607375"
        );

        assertTrue(result.matchesExpectedConversation());
        assertFalse(result.canonicalRedirect());
        assertEquals(
                "01a0907b-ac02-72d6-8504-c97d3291a5c4",
                result.actualConversationId()
        );
    }

    @Test
    public void acceptsVintedCanonicalRedirectWhenReferrerProvesOldConversation() {
        var result = resolver.assess(
                "01a0907b-ac02-72d6-8504-c97d3291a5c4",
                "https://www.vinted.pl/inbox/500035197835?referrer=%2Finbox%2F01a0907b-ac02-72d6-8504-c97d3291a5c4"
        );

        assertTrue(result.matchesExpectedConversation());
        assertTrue(result.canonicalRedirect());
        assertEquals("500035197835", result.actualConversationId());
        assertEquals(
                "https://www.vinted.pl/inbox/500035197835",
                result.canonicalConversationUrl()
        );
    }

    @Test
    public void rejectsDifferentConversationWithoutMatchingReferrer() {
        var result = resolver.assess(
                "01a0907b-ac02-72d6-8504-c97d3291a5c4",
                "https://www.vinted.pl/inbox/500035197835?referrer=%2Finbox%2Funrelated"
        );

        assertFalse(result.matchesExpectedConversation());
        assertFalse(result.canonicalRedirect());
        assertEquals("500035197835", result.actualConversationId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonVintedConversationUrl() {
        resolver.assess(
                "01a0907b-ac02-72d6-8504-c97d3291a5c4",
                "https://example.com/inbox/500035197835?referrer=%2Finbox%2F01a0907b-ac02-72d6-8504-c97d3291a5c4"
        );
    }
}

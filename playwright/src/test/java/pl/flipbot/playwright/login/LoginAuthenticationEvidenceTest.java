package pl.flipbot.playwright.login;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class LoginAuthenticationEvidenceTest {

    @Test
    public void absenceOfKnownControlsIsNotAuthenticationEvidence() {
        Page page = pageWithAuthSignals(false, false);

        String signal = new LoginService(null)
                .authenticatedSignal(page);

        assertNull(signal);
    }

    @Test
    public void visibleConversationsButtonIsStrongAuthenticationEvidence() {
        Page page = pageWithAuthSignals(true, false);

        assertEquals(
                "header-conversations-button",
                new LoginService(null).authenticatedSignal(page)
        );
    }

    @Test
    public void visibleInboxLinkIsStrongAuthenticationEvidence() {
        Page page = pageWithAuthSignals(false, true);

        assertEquals(
                "visible /inbox link",
                new LoginService(null).authenticatedSignal(page)
        );
    }

    private Page pageWithAuthSignals(
            boolean conversationsVisible,
            boolean inboxVisible
    ) {
        Page page = mock(Page.class);

        Locator conversations = locator(conversationsVisible);
        Locator inbox = locator(inboxVisible);

        when(page.getByTestId(
                LoginSelectors.CONVERSATIONS_BUTTON
        )).thenReturn(conversations);

        when(page.locator(
                "a[href*='/inbox']:visible"
        )).thenReturn(inbox);

        return page;
    }

    private Locator locator(
            boolean visible
    ) {
        Locator locator = mock(Locator.class);

        when(locator.count()).thenReturn(
                visible ? 1 : 0
        );

        if (visible) {
            when(locator.nth(0)).thenReturn(locator);
            when(locator.isVisible()).thenReturn(true);
        }

        return locator;
    }
}

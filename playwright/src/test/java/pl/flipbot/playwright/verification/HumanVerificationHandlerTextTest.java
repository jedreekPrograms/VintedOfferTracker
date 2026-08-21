package pl.flipbot.playwright.verification;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HumanVerificationHandlerTextTest {

    @Test
    void strongVisibleHumanTextIsRecognized() {
        assertEquals(
                "verify you are human",
                HumanVerificationHandler.matchingStrongText(
                        "Please VERIFY YOU ARE HUMAN before continuing"
                )
        );
    }

    @Test
    void genericSecurityTextDoesNotFreezeNormalBodyContent() {
        assertNull(
                HumanVerificationHandler.matchingStrongText(
                        "Security check provided by a background widget"
                )
        );
        assertNull(
                HumanVerificationHandler.matchingStrongText(
                        "Just a moment while this component loads"
                )
        );
    }

    @Test
    void genericChallengeMarkersRemainValidForPageTitleOnly() {
        assertEquals(
                "just a moment",
                HumanVerificationHandler.matchingTitleOnlyText(
                        "Just a moment..."
                )
        );
        assertEquals(
                "security check",
                HumanVerificationHandler.matchingTitleOnlyText(
                        "Security Check"
                )
        );
    }
}

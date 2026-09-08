package pl.flipbot.playwright.verification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class HumanVerificationHandlerTextTest {

    @Test
    public void strongVisibleHumanTextIsRecognized() {
        assertEquals(
                "verify you are human",
                HumanVerificationHandler.matchingStrongText(
                        "Please VERIFY YOU ARE HUMAN before continuing"
                )
        );

        assertEquals(
                "przesuń w prawo, aby zabezpieczyć dostęp",
                HumanVerificationHandler.matchingStrongText(
                        "Przesuń w prawo, aby zabezpieczyć dostęp"
                )
        );
    }

    @Test
    public void genericSecurityTextDoesNotFreezeNormalBodyContent() {
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
    public void genericChallengeMarkersRemainValidForPageTitleOnly() {
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

    @Test
    public void dataDomeChallengeIframesAreIncludedInVisibleDetection() {
        String selector = HumanVerificationHandler.verificationIframeSelector();

        assertTrue(selector.contains("captcha-delivery.com/captcha"));
        assertTrue(selector.contains("ddChallengeBody"));
        assertTrue(selector.contains("Verification system"));
    }
}

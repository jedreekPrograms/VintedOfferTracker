package pl.flipbot.playwright.verification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class HumanVerificationHandlerTextTest {

    @Test
    public void strongVisibleHumanTextIsRecognized() {
        assertEquals(
                "verify you are human",
                HumanVerificationHandler.matchingStrongText(
                        "Please VERIFY YOU ARE HUMAN before continuing"
                )
        );
    }

    @Test
    public void polishSliderChallengeIsRecognized() {
        assertEquals(
                "potwierdź, że jesteś człowiekiem",
                HumanVerificationHandler.matchingStrongText(
                        "Potwierdź, że jesteś człowiekiem"
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
    public void verificationTimeoutDefaultsToTenMinutes() {
        // Environment override is intentionally not mutated by this unit test.
        // In normal CI there is no override, so this verifies the safe default.
        assertEquals(
                600L,
                HumanVerificationHandler.verificationTimeoutSeconds()
        );
    }
}

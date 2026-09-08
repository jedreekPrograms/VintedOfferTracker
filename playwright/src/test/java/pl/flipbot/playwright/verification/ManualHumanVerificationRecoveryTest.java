package pl.flipbot.playwright.verification;

import org.junit.Test;
import pl.flipbot.playwright.marketplace.MarketplaceUrls;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ManualHumanVerificationRecoveryTest {

    @Test
    public void trustedVintedChallengePageIsReopenedExactly() {
        String challengeUrl =
                "https://www.vinted.pl/catalog?search_text=telefon";

        assertEquals(
                challengeUrl,
                ManualHumanVerificationRecovery.recoveryUrl(challengeUrl)
        );
    }

    @Test
    public void externalOrMissingChallengeUrlFallsBackToVintedHome() {
        assertEquals(
                MarketplaceUrls.HOME,
                ManualHumanVerificationRecovery.recoveryUrl(
                        "https://captcha-delivery.com/captcha/unsafe"
                )
        );
        assertEquals(
                MarketplaceUrls.HOME,
                ManualHumanVerificationRecovery.recoveryUrl(null)
        );
    }

    @Test
    public void vintedAuthenticationChallengeReplaysInteractiveLogin() {
        assertTrue(
                ManualHumanVerificationRecovery.requiresInteractiveLoginReplay(
                        "https://www.vinted.pl/member/login/email?ref_url=%2F"
                )
        );
        assertTrue(
                ManualHumanVerificationRecovery.requiresInteractiveLoginReplay(
                        "https://www.vinted.pl/member/register/select_type"
                )
        );

        assertFalse(
                ManualHumanVerificationRecovery.requiresInteractiveLoginReplay(
                        "https://www.vinted.pl/catalog?catalog[]=3661"
                )
        );
        assertFalse(
                ManualHumanVerificationRecovery.requiresInteractiveLoginReplay(
                        "https://www.vinted.pl.evil.example/member/login/email"
                )
        );
    }
}

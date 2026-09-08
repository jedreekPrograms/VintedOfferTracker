package pl.flipbot.playwright.verification;

import org.junit.Test;
import pl.flipbot.playwright.marketplace.MarketplaceUrls;

import static org.junit.Assert.assertEquals;

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
}

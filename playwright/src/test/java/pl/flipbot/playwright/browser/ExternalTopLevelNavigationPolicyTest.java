package pl.flipbot.playwright.browser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ExternalTopLevelNavigationPolicyTest {

    @Test
    public void blocksOrdinaryExternalHttpDestinations() {
        assertTrue(ExternalTopLevelNavigationPolicy.shouldBlock(
                "https://example.com/ad-landing"
        ));
        assertTrue(ExternalTopLevelNavigationPolicy.shouldBlock(
                "https://ads.some-network.test/redirect"
        ));
    }

    @Test
    public void allowsVintedAndChallengeDestinations() {
        assertFalse(ExternalTopLevelNavigationPolicy.shouldBlock(
                "https://www.vinted.pl/inbox/123"
        ));
        assertFalse(ExternalTopLevelNavigationPolicy.shouldBlock(
                "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/"
        ));
        assertFalse(ExternalTopLevelNavigationPolicy.shouldBlock(
                "https://assets.hcaptcha.com/captcha/v1/"
        ));
        assertFalse(ExternalTopLevelNavigationPolicy.shouldBlock(
                "https://www.google.com/recaptcha/api2/anchor"
        ));
    }

    @Test
    public void ignoresNonNetworkSchemes() {
        assertFalse(ExternalTopLevelNavigationPolicy.shouldBlock(
                "about:blank"
        ));
        assertFalse(ExternalTopLevelNavigationPolicy.shouldBlock(
                "data:text/html,hello"
        ));
        assertFalse(ExternalTopLevelNavigationPolicy.shouldBlock(
                "blob:https://www.vinted.pl/123"
        ));
    }
}

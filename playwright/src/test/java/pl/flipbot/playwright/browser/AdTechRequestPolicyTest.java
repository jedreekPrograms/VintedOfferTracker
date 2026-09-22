package pl.flipbot.playwright.browser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdTechRequestPolicyTest {

    @Test
    public void blocksObservedVintedAdvertisingHostsAndSubdomains() {
        assertTrue(AdTechRequestPolicy.shouldBlock(
                "https://cs.seedtag.com/cookies-sync"
        ));
        assertTrue(AdTechRequestPolicy.shouldBlock(
                "https://abc.safeframe.googlesyndication.com/safeframe"
        ));
        assertTrue(AdTechRequestPolicy.shouldBlock(
                "https://ep2.adtrafficquality.google/pagead"
        ));
        assertTrue(AdTechRequestPolicy.shouldBlock(
                "https://u.openx.net/w/1.0/sd"
        ));
        assertTrue(AdTechRequestPolicy.shouldBlock(
                "https://prebid-server.pbstck.com/openrtb2/auction"
        ));
        assertTrue(AdTechRequestPolicy.shouldBlock(
                "https://cm.g.doubleclick.net/pixel"
        ));
        assertTrue(AdTechRequestPolicy.shouldBlock(
                "https://usync.4dex.io/sync"
        ));
        assertTrue(AdTechRequestPolicy.shouldBlock(
                "https://sync.3lift.com/cookie"
        ));
    }

    @Test
    public void neverBlocksVintedOrCaptchaInfrastructure() {
        assertFalse(AdTechRequestPolicy.shouldBlock(
                "https://www.vinted.pl/inbox/500035197835"
        ));
        assertFalse(AdTechRequestPolicy.shouldBlock(
                "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/"
        ));
        assertFalse(AdTechRequestPolicy.shouldBlock(
                "https://assets.hcaptcha.com/captcha/v1/app.js"
        ));
        assertFalse(AdTechRequestPolicy.shouldBlock(
                "https://www.google.com/recaptcha/api2/anchor"
        ));
        assertFalse(AdTechRequestPolicy.shouldBlock(
                "https://www.gstatic.com/recaptcha/releases/test/recaptcha__pl.js"
        ));
    }

    @Test
    public void doesNotBlockLookalikeHosts() {
        assertFalse(AdTechRequestPolicy.shouldBlock(
                "https://doubleclick.net.example.com/path"
        ));
        assertFalse(AdTechRequestPolicy.shouldBlock(
                "https://seedtag.com.example.org/path"
        ));
        assertFalse(AdTechRequestPolicy.shouldBlock(
                "data:text/html,seedtag.com"
        ));
    }
}

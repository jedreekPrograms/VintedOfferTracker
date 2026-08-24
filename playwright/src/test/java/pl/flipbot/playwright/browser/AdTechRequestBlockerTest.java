package pl.flipbot.playwright.browser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdTechRequestBlockerTest {

    @Test
    public void blocksAdaptMxCookieSyncHostAndSubdomains() {
        assertTrue(AdTechRequestBlocker.shouldBlock(
                "https://euw2-sync.a-mo.net/sync?partner=example"
        ));
        assertTrue(AdTechRequestBlocker.shouldBlock(
                "https://a-mo.net/"
        ));
        assertTrue(AdTechRequestBlocker.shouldBlockHost(
                "EUW2-SYNC.A-MO.NET"
        ));
    }

    @Test
    public void blocksKnownAdvertisingHosts() {
        assertTrue(AdTechRequestBlocker.shouldBlock(
                "https://securepubads.g.doubleclick.net/tag/js/gpt.js"
        ));
        assertTrue(AdTechRequestBlocker.shouldBlock(
                "https://gum.criteo.com/sync"
        ));
        assertTrue(AdTechRequestBlocker.shouldBlock(
                "https://ads.pubmatic.com/AdServer/js/showad.js"
        ));
    }

    @Test
    public void doesNotBlockMarketplaceOrLookalikeHosts() {
        assertFalse(AdTechRequestBlocker.shouldBlock(
                "https://www.vinted.pl/items/123-test"
        ));
        assertFalse(AdTechRequestBlocker.shouldBlock(
                "https://images1.vinted.net/t/03_abc/image.jpg"
        ));
        assertFalse(AdTechRequestBlocker.shouldBlock(
                "https://a-mo.net.attacker.example/path"
        ));
        assertFalse(AdTechRequestBlocker.shouldBlock(
                "https://example.com/?next=https://euw2-sync.a-mo.net"
        ));
    }

    @Test
    public void malformedOrMissingUrlsFailOpen() {
        assertFalse(AdTechRequestBlocker.shouldBlock(null));
        assertFalse(AdTechRequestBlocker.shouldBlock(""));
        assertFalse(AdTechRequestBlocker.shouldBlock("not a url"));
    }

    @Test
    public void adTechLoggingIsRateLimited() {
        assertTrue(BrowserManager.shouldLogBlockedAdTechRequest(1));
        assertTrue(BrowserManager.shouldLogBlockedAdTechRequest(3));
        assertFalse(BrowserManager.shouldLogBlockedAdTechRequest(4));
        assertTrue(BrowserManager.shouldLogBlockedAdTechRequest(100));
        assertFalse(BrowserManager.shouldLogBlockedAdTechRequest(101));
    }
}

package pl.flipbot.playwright.browser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CatalogHeavyResourcePolicyTest {

    private static final String CATALOG =
            "https://www.vinted.pl/catalog?catalog[]=3661";

    private static final String ITEM =
            "https://www.vinted.pl/items/123456789-test-item";

    private static final String INBOX =
            "https://www.vinted.pl/inbox";

    private static final String CONVERSATION =
            "https://www.vinted.pl/inbox/987654321";

    @Test
    public void blocksCatalogImagesAndMedia() {
        assertTrue(CatalogHeavyResourcePolicy.shouldBlock(
                CATALOG,
                "image",
                "https://images1.vinted.net/t/01.jpg"
        ));
        assertTrue(CatalogHeavyResourcePolicy.shouldBlock(
                CATALOG,
                "media",
                "https://cdn.example.test/video.mp4"
        ));
    }

    @Test
    public void blocksItemDetailImagesAndMedia() {
        assertTrue(CatalogHeavyResourcePolicy.shouldBlock(
                ITEM,
                "image",
                "https://images1.vinted.net/t/01.jpg"
        ));
        assertTrue(CatalogHeavyResourcePolicy.shouldBlock(
                ITEM,
                "media",
                "https://cdn.example.test/video.mp4"
        ));
    }

    @Test
    public void blocksInboxAndConversationImagesAndMedia() {
        assertTrue(CatalogHeavyResourcePolicy.shouldBlock(
                INBOX,
                "image",
                "https://images1.vinted.net/avatar.jpg"
        ));
        assertTrue(CatalogHeavyResourcePolicy.shouldBlock(
                CONVERSATION,
                "media",
                "https://cdn.example.test/conversation-video.mp4"
        ));
    }

    @Test
    public void neverBlocksFunctionalTrafficOnOptimizedPages() {
        for (String pageUrl : new String[]{CATALOG, ITEM, INBOX, CONVERSATION}) {
            assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                    pageUrl,
                    "document",
                    pageUrl
            ));
            assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                    pageUrl,
                    "script",
                    "https://www.vinted.pl/assets/app.js"
            ));
            assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                    pageUrl,
                    "xhr",
                    "https://www.vinted.pl/api/v2/items"
            ));
            assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                    pageUrl,
                    "stylesheet",
                    "https://www.vinted.pl/assets/app.css"
            ));
        }
    }

    @Test
    public void doesNotBlockHeavyResourcesOnHomeOrSessionRefresh() {
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                "https://www.vinted.pl/",
                "image",
                "https://images1.vinted.net/home.jpg"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                "https://www.vinted.pl/session-refresh",
                "image",
                "https://images1.vinted.net/refresh.jpg"
        ));
    }

    @Test
    public void lookalikeVintedHostsAreNeverOptimized() {
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                "https://www.vinted.pl.example.com/catalog",
                "image",
                "https://images1.vinted.net/item.jpg"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                "https://www.vinted.pl.example.com/items/123-test",
                "image",
                "https://images1.vinted.net/item.jpg"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                "https://www.vinted.pl.example.com/inbox/123",
                "image",
                "https://images1.vinted.net/avatar.jpg"
        ));
    }

    @Test
    public void challengeAssetsRemainAvailableOnOptimizedPages() {
        for (String pageUrl : new String[]{CATALOG, ITEM, CONVERSATION}) {
            assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                    pageUrl,
                    "image",
                    "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/image.png"
            ));
            assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                    pageUrl,
                    "image",
                    "https://assets.hcaptcha.com/captcha/image.png"
            ));
            assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                    pageUrl,
                    "image",
                    "https://www.recaptcha.net/recaptcha/api2/payload.png"
            ));
            assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                    pageUrl,
                    "image",
                    "https://www.gstatic.com/recaptcha/api2/logo_48.png"
            ));
            assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                    pageUrl,
                    "image",
                    "https://www.google.com/recaptcha/api2/payload.png"
            ));
        }
    }
}

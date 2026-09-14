package pl.flipbot.playwright.browser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CatalogHeavyResourcePolicyTest {

    private static final String CATALOG =
            "https://www.vinted.pl/catalog?catalog[]=3661";

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
    public void neverBlocksFunctionalCatalogTraffic() {
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                CATALOG,
                "document",
                "https://www.vinted.pl/catalog"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                CATALOG,
                "script",
                "https://www.vinted.pl/assets/app.js"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                CATALOG,
                "xhr",
                "https://www.vinted.pl/api/v2/catalog/items"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                CATALOG,
                "stylesheet",
                "https://www.vinted.pl/assets/app.css"
        ));
    }

    @Test
    public void doesNotBlockHeavyResourcesOutsideCatalog() {
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                "https://www.vinted.pl/",
                "image",
                "https://images1.vinted.net/home.jpg"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                "https://www.vinted.pl/inbox",
                "image",
                "https://images1.vinted.net/avatar.jpg"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                "https://www.vinted.pl/items/123-phone",
                "image",
                "https://images1.vinted.net/item.jpg"
        ));
    }

    @Test
    public void lookalikeCatalogHostIsNeverOptimized() {
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                "https://www.vinted.pl.example.com/catalog",
                "image",
                "https://images1.vinted.net/item.jpg"
        ));
    }

    @Test
    public void challengeAssetsRemainAvailableOnCatalog() {
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                CATALOG,
                "image",
                "https://challenges.cloudflare.com/cdn-cgi/challenge-platform/image.png"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                CATALOG,
                "image",
                "https://assets.hcaptcha.com/captcha/image.png"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                CATALOG,
                "image",
                "https://www.recaptcha.net/recaptcha/api2/payload.png"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                CATALOG,
                "image",
                "https://www.gstatic.com/recaptcha/api2/logo_48.png"
        ));
        assertFalse(CatalogHeavyResourcePolicy.shouldBlock(
                CATALOG,
                "image",
                "https://www.google.com/recaptcha/api2/payload.png"
        ));
    }
}

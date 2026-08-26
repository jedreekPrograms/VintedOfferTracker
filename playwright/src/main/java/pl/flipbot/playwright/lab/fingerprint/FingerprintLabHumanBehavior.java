package pl.flipbot.playwright.lab.fingerprint;

import com.microsoft.playwright.BoundingBox;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Human-interaction simulator for the controlled fingerprint laboratory.
 *
 * Every public operation re-checks the current page URL and refuses to operate
 * outside loopback / reserved test hosts. It is intentionally unavailable to
 * marketplace pages.
 */
public final class FingerprintLabHumanBehavior {

    private FingerprintLabHumanBehavior() {}

    public static void exercise(Page page) {
        requireLaboratoryPage(page);

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double viewportWidth = numberFromPage(
                page,
                "() => Math.max(window.innerWidth, 640)"
        );
        double viewportHeight = numberFromPage(
                page,
                "() => Math.max(window.innerHeight, 480)"
        );

        moveNaturally(
                page,
                viewportWidth * 0.16,
                viewportHeight * 0.18,
                random.nextInt(14, 25)
        );
        pause(page, random.nextInt(180, 520));

        moveNaturally(
                page,
                viewportWidth * 0.62,
                viewportHeight * 0.34,
                random.nextInt(18, 31)
        );
        pause(page, random.nextInt(120, 420));

        scrollNaturally(page, random.nextInt(220, 520));
        pause(page, random.nextInt(280, 780));
        scrollNaturally(page, -random.nextInt(90, 230));

        Locator refresh = page.locator("#refresh");
        if (refresh.count() > 0 && refresh.isVisible()) {
            BoundingBox box = refresh.boundingBox();
            if (box != null) {
                moveNaturally(
                        page,
                        box.x + box.width / 2.0,
                        box.y + box.height / 2.0,
                        random.nextInt(12, 24)
                );
                pause(page, random.nextInt(90, 260));
                refresh.click();
            }
        }

        requireLaboratoryPage(page);
    }

    public static void typeLikeUser(
            Page page,
            String selector,
            String text
    ) {
        requireLaboratoryPage(page);
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("selector cannot be blank");
        }
        if (text == null) {
            throw new IllegalArgumentException("text cannot be null");
        }

        ThreadLocalRandom random = ThreadLocalRandom.current();
        Locator locator = page.locator(selector);
        locator.click();
        locator.pressSequentially(
                text,
                new Locator.PressSequentiallyOptions()
                        .setDelay(random.nextInt(55, 135))
        );
        pause(page, random.nextInt(160, 520));
        requireLaboratoryPage(page);
    }

    private static void moveNaturally(
            Page page,
            double targetX,
            double targetY,
            int steps
    ) {
        requireLaboratoryPage(page);
        page.mouse().move(
                targetX,
                targetY,
                new Mouse.MoveOptions().setSteps(steps)
        );
    }

    private static void scrollNaturally(Page page, double deltaY) {
        requireLaboratoryPage(page);
        page.mouse().wheel(0, deltaY);
    }

    private static void pause(Page page, int millis) {
        requireLaboratoryPage(page);
        page.waitForTimeout(millis);
    }

    private static double numberFromPage(Page page, String expression) {
        Object value = page.evaluate(expression);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalStateException(
                "Fingerprint lab expected numeric page value but got: " + value
        );
    }

    private static void requireLaboratoryPage(Page page) {
        if (page == null) {
            throw new IllegalArgumentException("page cannot be null");
        }
        FingerprintLabPolicy.requireAllowed(page.url());
    }
}

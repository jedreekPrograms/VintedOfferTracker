package pl.flipbot.playwright.login;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class LoginCookieFastPathTest {
    @Test public void absentBannerDoesNotWaitOrClick() {
        AtomicInteger clicks = new AtomicInteger();
        LoginService.acceptCookiesIfVisible(page(false, false, clicks));
        assertEquals(0, clicks.get());
    }

    @Test public void visibleBannerIsAcceptedWithABoundedClick() {
        AtomicInteger clicks = new AtomicInteger();
        LoginService.acceptCookiesIfVisible(page(true, false, clicks));
        assertEquals(1, clicks.get());
    }

    @Test public void GuardDismissingBannerBetweenCheckAndClickDoesNotFailLogin() {
        AtomicInteger clicks = new AtomicInteger();
        LoginService.acceptCookiesIfVisible(page(true, true, clicks));
        assertEquals(1, clicks.get());
    }

    private Page page(boolean visible, boolean vanished, AtomicInteger clicks) {
        Locator locator = (Locator) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{Locator.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isVisible")) return visible;
                    if (method.getName().equals("click")) {
                        assertEquals(1_500.0, ((Locator.ClickOptions) args[0]).timeout, 0.0);
                        clicks.incrementAndGet();
                        if (vanished) throw new RuntimeException("Banner was dismissed by consent guard");
                        return null;
                    }
                    throw new AssertionError("Unexpected blocking locator operation: " + method.getName());
                });
        return (Page) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Page.class},
                (proxy, method, args) -> {
                    assertEquals("locator", method.getName());
                    assertEquals("#onetrust-accept-btn-handler", args[0]);
                    return locator;
                });
    }
}

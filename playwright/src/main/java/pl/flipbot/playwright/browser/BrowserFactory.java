package pl.flipbot.playwright.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import java.util.List;

public class BrowserFactory {

    private static final String PLAYWRIGHT_DISABLE_POPUP_BLOCKING_ARG =
            "--disable-popup-blocking";

    private static final List<String> IGNORED_PLAYWRIGHT_DEFAULT_ARGS =
            List.of(PLAYWRIGHT_DISABLE_POPUP_BLOCKING_ARG);

    private BrowserFactory() {}

    public static Browser createBrowser(
            Playwright playwright
    ) {
        return createBrowser(playwright, false);
    }

    public static Browser createBrowser(
            Playwright playwright,
            boolean headless
    ) {
        /*
         * Playwright normally launches Chromium/Chrome with
         * --disable-popup-blocking. That is useful for browser tests, but it is
         * the opposite of what FlipBot wants in production: ad-tech embedded
         * on marketplace pages can create large popup/redirect storms that a
         * normal Chrome session would suppress.
         *
         * Ignore ONLY that one Playwright default argument. All other
         * Playwright defaults remain untouched, and BotContext keeps its
         * single-page guard as a second line of defence for any popup Chrome
         * still permits after a genuine user-gesture-style click.
         */
        return playwright.chromium()
                .launch(
                        new BrowserType.LaunchOptions()
                                .setHeadless(headless)
                                .setChannel("chrome")
                                .setIgnoreDefaultArgs(
                                        IGNORED_PLAYWRIGHT_DEFAULT_ARGS
                                )
                );
    }

    static List<String> ignoredPlaywrightDefaultArgs() {
        return IGNORED_PLAYWRIGHT_DEFAULT_ARGS;
    }
}

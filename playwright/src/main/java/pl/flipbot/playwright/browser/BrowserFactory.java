package pl.flipbot.playwright.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
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
        if (BrowserResourceOptimizationConfig.usePlaywrightChromium(headless)) {
            try {
                log.info(
                        "[BROWSER MEMORY] Trying Playwright Chromium/headless shell because {}=true.",
                        BrowserResourceOptimizationConfig.USE_PLAYWRIGHT_CHROMIUM_ENV
                );
                return launchBrowser(playwright, headless, false);
            } catch (RuntimeException playwrightChromiumFailure) {
                log.warn(
                        "[BROWSER MEMORY] Playwright Chromium/headless shell could not be launched. Falling back to system Google Chrome. reason={}",
                        safeMessage(playwrightChromiumFailure)
                );

                try {
                    return launchBrowser(playwright, headless, true);
                } catch (RuntimeException chromeFailure) {
                    chromeFailure.addSuppressed(playwrightChromiumFailure);
                    throw chromeFailure;
                }
            }
        }

        return launchBrowser(playwright, headless, true);
    }

    private static Browser launchBrowser(
            Playwright playwright,
            boolean headless,
            boolean useChromeChannel
    ) {
        BrowserType.LaunchOptions options =
                new BrowserType.LaunchOptions()
                        .setHeadless(headless)
                        .setIgnoreDefaultArgs(
                                IGNORED_PLAYWRIGHT_DEFAULT_ARGS
                        );

        if (useChromeChannel) {
            options.setChannel("chrome");
        }

        return playwright.chromium().launch(options);
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return throwable == null
                    ? "unknown error"
                    : throwable.getClass().getSimpleName();
        }
        return throwable.getMessage();
    }

    static List<String> ignoredPlaywrightDefaultArgs() {
        return IGNORED_PLAYWRIGHT_DEFAULT_ARGS;
    }
}

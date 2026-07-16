package pl.flipbot.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pl.flipbot.playwright.browser.BrowserFactory;
import pl.flipbot.playwright.browser.BrowserManager;

import java.nio.file.Paths;

public class FlipBotPlaywrightApplication {

    private static final Logger log =
            LoggerFactory.getLogger(
                    FlipBotPlaywrightApplication.class
            );

    public static void main(String[] args) {

        log.info("Starting FlipBot Playwright...");

        try (BrowserManager browserManager =
                new BrowserManager()) {

            Page page = browserManager.newPage();

            page.navigate("https://example.com");

            log.info("Title: {}", page.title());

            page.screenshot(
                    new Page.ScreenshotOptions()
                            .setPath(
                                    Paths.get("example.png")
                            )
            );
        }
    }
}

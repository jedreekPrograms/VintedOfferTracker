package pl.flipbot.playwright.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
public class BrowserManager implements AutoCloseable {

    private final long ownerThreadId;

    private final String ownerThreadName;

    private final Playwright playwright;

    private final Browser browser;

    private boolean closed =
            false;


    public BrowserManager() {

        this.ownerThreadId =
                Thread.currentThread()
                        .threadId();

        this.ownerThreadName =
                Thread.currentThread()
                        .getName();


        log.info(
                "[BROWSER] Creating Playwright runtime on thread {} (id={}).",
                ownerThreadName,
                ownerThreadId
        );


        this.playwright =
                Playwright.create();

        this.browser =
                BrowserFactory.createBrowser(
                        playwright
                );
    }


    public BrowserContext createContext(
            Path storageState
    ) {

        assertOwnerThread(
                "create browser context"
        );

        assertOpen();


        Browser.NewContextOptions options =
                new Browser.NewContextOptions();


        if (
                storageState != null
        ) {

            options.setStorageStatePath(
                    storageState
            );
        }


        return browser.newContext(
                options
        );
    }


    @Override
    public void close() {

        assertOwnerThread(
                "close Playwright runtime"
        );


        if (
                closed
        ) {

            return;
        }


        closed =
                true;


        log.info(
                "[BROWSER] Closing Playwright runtime on thread {} (id={}).",
                ownerThreadName,
                ownerThreadId
        );


        try {

            browser.close();

        } catch (Exception exception) {

            log.warn(
                    "[BROWSER] Could not close browser cleanly.",
                    exception
            );

        } finally {

            try {

                playwright.close();

            } catch (Exception exception) {

                log.warn(
                        "[BROWSER] Could not close Playwright cleanly.",
                        exception
                );
            }
        }
    }


    private void assertOwnerThread(
            String operation
    ) {

        Thread currentThread =
                Thread.currentThread();


        if (
                currentThread.threadId()
                        == ownerThreadId
        ) {

            return;
        }


        throw new IllegalStateException(
                "BrowserManager attempted to "
                        + operation
                        + " from thread "
                        + currentThread.getName()
                        + " (id="
                        + currentThread.threadId()
                        + "), but its Playwright runtime belongs to thread "
                        + ownerThreadName
                        + " (id="
                        + ownerThreadId
                        + ")."
        );
    }


    private void assertOpen() {

        if (
                closed
        ) {

            throw new IllegalStateException(
                    "BrowserManager is already closed."
            );
        }
    }
}

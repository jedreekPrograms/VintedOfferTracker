package pl.flipbot.playwright.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class BrowserManager implements AutoCloseable {

    private static final int AD_TECH_LOG_FIRST_EVENTS = 3;
    private static final int AD_TECH_LOG_INTERVAL = 100;

    private final long ownerThreadId;
    private final String ownerThreadName;
    private final Playwright playwright;
    private final Browser browser;
    private final boolean headless;

    private boolean closed = false;

    public BrowserManager() {
        this(false);
    }

    public BrowserManager(boolean headless) {
        this.ownerThreadId = Thread.currentThread().threadId();
        this.ownerThreadName = Thread.currentThread().getName();
        this.headless = headless;

        log.info(
                "[BROWSER] Creating Playwright runtime on thread {} (id={}), headless={}.",
                ownerThreadName,
                ownerThreadId,
                headless
        );

        Playwright createdPlaywright = Playwright.create();
        Browser createdBrowser;

        try {
            createdBrowser = BrowserFactory.createBrowser(
                    createdPlaywright,
                    headless
            );
        } catch (RuntimeException exception) {
            try {
                createdPlaywright.close();
            } catch (Exception closeException) {
                exception.addSuppressed(closeException);
            }

            throw exception;
        }

        this.playwright = createdPlaywright;
        this.browser = createdBrowser;
    }

    public BrowserContext createContext(String storageState) {
        assertOwnerThread("create browser context");
        assertOpen();

        Browser.NewContextOptions options =
                new Browser.NewContextOptions();

        /*
         * Storage state is supplied as an in-memory JSON string. SessionManager
         * is responsible for authenticated encryption at rest, so BrowserManager
         * must never point Playwright at a persistent plaintext cookie file.
         */
        if (storageState != null && !storageState.isBlank()) {
            options.setStorageState(storageState);
        }

        BrowserContext context = browser.newContext(options);

        installAdTechNetworkBlocker(context);
        context.addInitScript(VintedInformationalDialogGuard.script());

        log.debug(
                "[BROWSER UI] Vinted informational-dialog guard installed for new browser context."
        );

        return context;
    }

    private void installAdTechNetworkBlocker(BrowserContext context) {
        AtomicInteger blockedRequests = new AtomicInteger();

        /*
         * BotContext already prevents window.open(), target=_blank links/forms
         * and immediately closes any extra Page that still reaches Chromium.
         * This network guard sits one layer earlier for known advertising/RTB
         * infrastructure: iframe, fetch, image, script and navigation requests
         * are aborted before the remote ad-tech host can be contacted.
         *
         * The matcher is deliberately host-only and allow-by-default. We do not
         * block arbitrary third-party/CDN traffic used by the marketplace.
         */
        context.route(
                "**/*",
                route -> {
                    String requestUrl = route.request().url();

                    if (!AdTechRequestBlocker.shouldBlock(requestUrl)) {
                        route.resume();
                        return;
                    }

                    int blockedNumber = blockedRequests.incrementAndGet();

                    if (shouldLogBlockedAdTechRequest(blockedNumber)) {
                        log.info(
                                "[AD BLOCK] Aborting ad-tech request before network access. event={}, url={}",
                                blockedNumber,
                                requestUrl
                        );
                    } else {
                        log.debug(
                                "[AD BLOCK] Suppressed ad-tech request log. event={}, url={}",
                                blockedNumber,
                                requestUrl
                        );
                    }

                    route.abort();
                }
        );

        log.info(
                "[AD BLOCK] Pre-network ad-tech filtering enabled for new browser context. "
                        + "Known advertising/RTB hosts are aborted before navigation/request completion."
        );
    }

    static boolean shouldLogBlockedAdTechRequest(int eventNumber) {
        return eventNumber <= AD_TECH_LOG_FIRST_EVENTS
                || eventNumber % AD_TECH_LOG_INTERVAL == 0;
    }

    @Override
    public void close() {
        assertOwnerThread("close Playwright runtime");

        if (closed) {
            return;
        }

        closed = true;

        log.info(
                "[BROWSER] Closing Playwright runtime on thread {} (id={}), headless={}.",
                ownerThreadName,
                ownerThreadId,
                headless
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

    private void assertOwnerThread(String operation) {
        Thread currentThread = Thread.currentThread();

        if (currentThread.threadId() == ownerThreadId) {
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
        if (closed) {
            throw new IllegalStateException(
                    "BrowserManager is already closed."
            );
        }
    }
}

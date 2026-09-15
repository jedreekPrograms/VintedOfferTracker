package pl.flipbot.playwright.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ServiceWorkerPolicy;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
public class BrowserManager implements AutoCloseable {

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

    public BrowserContext createContext(Path storageState) {
        assertOwnerThread("create browser context");
        assertOpen();

        Browser.NewContextOptions options =
                new Browser.NewContextOptions();

        if (storageState != null) {
            options.setStorageStatePath(storageState);
        }

        boolean blockServiceWorkers =
                BrowserResourceOptimizationConfig.blockServiceWorkers(headless);
        if (blockServiceWorkers) {
            options.setServiceWorkers(ServiceWorkerPolicy.BLOCK);
        }

        BrowserContext context;

        try {
            context = browser.newContext(options);
        } catch (RuntimeException exception) {
            if (storageState == null) {
                throw exception;
            }

            /*
             * A stored bot session is authoritative. Do not expose the raw
             * Playwright restore exception as a recoverable storage-state
             * failure to BotContext, because that legacy path falls back to
             * createContext(null) and can silently turn a logged-in bot into a
             * fresh browser session. Keep the original error as suppressed
             * diagnostic information, leave bot-X.json untouched and fail the
             * job instead.
             */
            IllegalStateException guardedFailure = new IllegalStateException(
                    "Stored bot session could not be restored from "
                            + storageState
                            + ". Refusing automatic clean browser context fallback; the saved session was left untouched."
            );
            guardedFailure.addSuppressed(exception);
            throw guardedFailure;
        }

        context.addInitScript(VintedInformationalDialogGuard.script());
        context.addInitScript(OneTrustConsentGuard.script());

        if (headless) {
            installCatalogHeavyResourceGuard(context);
        }

        if (blockServiceWorkers) {
            log.info(
                    "[BROWSER MEMORY] Service-worker registration is blocked for this headless browser context. Set {}=false to disable this optimization.",
                    BrowserResourceOptimizationConfig.BLOCK_SERVICE_WORKERS_ENV
            );
        }

        log.debug(
                "[BROWSER UI] Vinted informational-dialog and OneTrust consent guards installed for new browser context."
        );

        return context;
    }

    private void installCatalogHeavyResourceGuard(BrowserContext context) {
        context.route(
                "**/*",
                route -> {
                    try {
                        var request = route.request();
                        String topLevelPageUrl = request.frame().page().url();

                        if (CatalogHeavyResourcePolicy.shouldBlock(
                                topLevelPageUrl,
                                request.resourceType(),
                                request.url()
                        )) {
                            route.abort();
                            return;
                        }
                    } catch (RuntimeException exception) {
                        /*
                         * This optimization is never allowed to make browser
                         * correctness depend on route-inspection details. If a
                         * frame/page is changing during navigation, fail open
                         * and let Chromium load the resource normally.
                         */
                        log.trace(
                                "[BROWSER MEMORY] Could not classify a resource request safely; allowing it.",
                                exception
                        );
                    }

                    route.resume();
                }
        );

        log.info(
                "[BROWSER MEMORY] Headless heavy-resource guard installed. Vinted catalog/item-detail image and media transfers may be skipped; functional traffic and challenge assets remain enabled."
        );
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

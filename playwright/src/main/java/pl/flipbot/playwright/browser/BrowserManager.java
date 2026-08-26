package pl.flipbot.playwright.browser;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.session.SessionManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

    public BrowserContext createContext(Path sessionReference) {
        assertOwnerThread("create browser context");
        assertOpen();

        Browser.NewContextOptions options =
                new Browser.NewContextOptions();

        /*
         * sessionReference points to SessionManager-managed encrypted material,
         * not to a plaintext Playwright storage-state file. Resolve/decrypt it
         * to RAM and use Playwright's String API so cookies/localStorage are
         * never written to a temporary plaintext file.
         *
         * Upgrade safety matters here: if encrypted state cannot be opened
         * because a worker process has a missing/wrong key, the stored file is
         * left untouched and this run starts with a clean context. LoginService
         * can then authenticate normally. SessionManager refuses to overwrite an
         * existing encrypted file unless the current key can authenticate it,
         * so this fallback cannot destroy a valid session under an accidental
         * key. For a legacy plaintext .json, migration failure is allowed to
         * fall back to that existing state for this run so an upgrade does not
         * unexpectedly log every account out.
         */
        if (sessionReference != null) {
            String storageState = resolveStorageStateForRestore(
                    sessionReference
            );

            if (storageState != null) {
                options.setStorageState(storageState);
            }
        }

        BrowserContext context = browser.newContext(options);

        context.addInitScript(VintedInformationalDialogGuard.script());

        log.debug(
                "[BROWSER UI] Vinted informational-dialog guard installed for new browser context."
        );

        return context;
    }

    static String resolveStorageStateForRestore(Path sessionReference) {
        try {
            return SessionManager.readStorageStateFromReference(
                    sessionReference
            );
        } catch (RuntimeException exception) {
            if (isLegacyPlaintextReference(sessionReference)) {
                try {
                    String legacyStorageState = Files.readString(
                            sessionReference,
                            StandardCharsets.UTF_8
                    );

                    log.error(
                            "[SESSION] Could not migrate legacy plaintext session {} to encrypted storage. "
                                    + "Using the existing legacy state in memory for this run and leaving the file untouched. "
                                    + "Configure FLIPBOT_SESSION_ENCRYPTION_KEY or FLIPBOT_ENCRYPTION_KEY to complete migration. reason={}",
                            sessionReference,
                            safeMessage(exception)
                    );

                    return legacyStorageState;
                } catch (IOException legacyReadException) {
                    exception.addSuppressed(legacyReadException);
                }
            }

            log.error(
                    "[SESSION] Stored session {} could not be safely restored. "
                            + "The file is left untouched and this browser job will start with a clean context. "
                            + "If credentials are valid, normal login can continue the job. reason={}",
                    sessionReference,
                    safeMessage(exception)
            );

            return null;
        }
    }

    private static boolean isLegacyPlaintextReference(Path sessionReference) {
        if (sessionReference == null || sessionReference.getFileName() == null) {
            return false;
        }

        return sessionReference.getFileName()
                .toString()
                .matches("bot-\\d+\\.json");
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return throwable == null
                    ? "unknown error"
                    : throwable.getClass().getSimpleName();
        }

        return throwable.getMessage()
                .lines()
                .findFirst()
                .orElse(throwable.getMessage())
                .trim();
    }

    public boolean isHealthy() {
        assertOwnerThread("check browser runtime health");

        if (closed) {
            return false;
        }

        try {
            return browser.isConnected();
        } catch (RuntimeException exception) {
            log.warn(
                    "[BROWSER] Could not verify browser connection health. The runtime will be treated as disconnected.",
                    exception
            );
            return false;
        }
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

package pl.flipbot.playwright.session;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.options.Cookie;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import pl.flipbot.playwright.browser.BrowserManager;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EncryptedSessionBrowserSmokeTest {

    private static final String SMOKE_ENV =
            "FLIPBOT_BROWSER_SESSION_SMOKE";

    private static final String KEY = Base64.getEncoder().encodeToString(
            "11111111111111111111111111111111"
                    .getBytes(StandardCharsets.UTF_8)
    );

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void actualChromiumRestoresCookieFromEncryptedSessionState()
            throws Exception {
        Assume.assumeTrue(
                "Real browser session smoke is enabled only in its dedicated CI job.",
                "true".equalsIgnoreCase(System.getenv(SMOKE_ENV))
        );

        Path sessions = temporaryFolder.newFolder("browser-session-smoke").toPath();
        SessionManager sessionManager = new SessionManager(sessions, KEY);

        try (BrowserManager browserManager = new BrowserManager(true)) {
            assertTrue(browserManager.isHealthy());

            BrowserContext firstContext = browserManager.createContext(null);

            try {
                firstContext.addCookies(
                        List.of(
                                new Cookie(
                                        "flipbot_session_smoke",
                                        "restored-from-encrypted-state"
                                ).setUrl("https://example.com")
                        )
                );

                sessionManager.saveSession(91L, firstContext);
            } finally {
                firstContext.close();
            }

            Path encrypted = sessionManager.encryptedSessionFile(91L);

            assertTrue(Files.exists(encrypted));
            assertFalse(Files.exists(sessionManager.legacySessionFile(91L)));
            assertFalse(
                    Files.readString(encrypted)
                            .contains("restored-from-encrypted-state")
            );

            BrowserContext restoredContext = browserManager.createContext(
                    sessionManager.sessionFile(91L)
            );

            try {
                Cookie restoredCookie = restoredContext.cookies()
                        .stream()
                        .filter(cookie -> "flipbot_session_smoke".equals(cookie.name))
                        .findFirst()
                        .orElseThrow();

                assertEquals(
                        "restored-from-encrypted-state",
                        restoredCookie.value
                );
            } finally {
                restoredContext.close();
            }

            assertTrue(browserManager.isHealthy());
        }
    }
}

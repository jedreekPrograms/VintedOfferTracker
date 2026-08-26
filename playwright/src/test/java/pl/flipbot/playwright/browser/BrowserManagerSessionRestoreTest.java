package pl.flipbot.playwright.browser;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BrowserManagerSessionRestoreTest {

    private static final String STORAGE_STATE = """
            {"cookies":[{"name":"auth_token","value":"legacy-cookie"}],"origins":[]}
            """.trim();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void legacyPlaintextRemainsUsableWhenAutomaticMigrationCannotComplete()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("legacy-fallback").toPath();
        Path legacy = sessions.resolve("bot-41.json");
        Files.writeString(legacy, STORAGE_STATE);

        String restored = BrowserManager.resolveStorageStateForRestore(legacy);

        assertEquals(STORAGE_STATE, restored);

        /*
         * With a configured valid key SessionManager may migrate the file during
         * this call. Without one, the legacy file must remain available instead
         * of making the worker fail. Both outcomes are safe upgrade paths.
         */
        assertTrue(
                Files.exists(legacy)
                        || Files.exists(sessions.resolve("bot-41.state.enc"))
        );
    }

    @Test
    public void unusableEncryptedSessionFallsBackToCleanContextWithoutDeletion()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("encrypted-fallback").toPath();
        Path encrypted = sessions.resolve("bot-42.state.enc");
        String corruptedCiphertext = "flipbot-session:v1:not-valid-base64";
        Files.writeString(encrypted, corruptedCiphertext);

        String restored = BrowserManager.resolveStorageStateForRestore(encrypted);

        assertNull(restored);
        assertTrue(Files.exists(encrypted));
        assertEquals(corruptedCiphertext, Files.readString(encrypted));
    }
}

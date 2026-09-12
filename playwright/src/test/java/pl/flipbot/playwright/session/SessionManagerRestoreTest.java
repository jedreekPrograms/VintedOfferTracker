package pl.flipbot.playwright.session;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionManagerRestoreTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void restoresLastKnownGoodAndPreservesRejectedActiveAsRecovery()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("restore-session").toPath();
        SessionManager manager = new SessionManager(sessions);
        Path active = manager.sessionFile(11L);
        Path backups = Files.createDirectories(sessions.resolve("backups"));
        Path lastKnownGood = backups.resolve("bot-11-last-known-good.json");

        byte[] rejected = "{\"cookies\":[{\"name\":\"session\",\"value\":\"degraded\"}],\"origins\":[]}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] healthy = "{\"cookies\":[{\"name\":\"session\",\"value\":\"healthy\"}],\"origins\":[]}"
                .getBytes(StandardCharsets.UTF_8);

        Files.write(active, rejected);
        Files.write(lastKnownGood, healthy);

        assertTrue(manager.restoreLastKnownGood(11L));

        assertArrayEquals(healthy, Files.readAllBytes(active));
        assertArrayEquals(healthy, Files.readAllBytes(lastKnownGood));
        assertArrayEquals(
                rejected,
                Files.readAllBytes(backups.resolve("bot-11-recovery.json"))
        );
    }

    @Test
    public void missingLastKnownGoodLeavesActiveSessionUntouched()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("missing-backup").toPath();
        SessionManager manager = new SessionManager(sessions);
        Path active = manager.sessionFile(11L);
        byte[] original = "{\"cookies\":[{\"name\":\"session\"}],\"origins\":[]}"
                .getBytes(StandardCharsets.UTF_8);

        Files.write(active, original);

        assertFalse(manager.restoreLastKnownGood(11L));
        assertArrayEquals(original, Files.readAllBytes(active));
        assertFalse(Files.exists(sessions.resolve("backups/bot-11-recovery.json")));
    }
}

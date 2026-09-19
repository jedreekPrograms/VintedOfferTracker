package pl.flipbot.playwright.session;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SessionManagerPersistenceGuardTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void zeroByteActiveSessionFailsClosedInsteadOfLookingMissing()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("zero-byte-active").toPath();
        SessionManager manager = new SessionManager(sessions);
        Files.createFile(manager.sessionFile(4L));

        try {
            manager.sessionExists(4L);
            fail("Zero-byte active session must fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Refusing to treat it as a missing session"));
        }
    }

    @Test
    public void malformedActiveSessionFailsClosedInsteadOfLookingMissing()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("malformed-active").toPath();
        SessionManager manager = new SessionManager(sessions);
        Files.writeString(manager.sessionFile(4L), "{broken-json");

        try {
            manager.sessionExists(4L);
            fail("Malformed active session must fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Could not validate stored session"));
        }
    }

    @Test
    public void cookieEmptySnapshotCannotReplaceEstablishedSession()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("cookie-loss").toPath();
        SessionManager manager = new SessionManager(sessions);
        Path active = manager.sessionFile(4L);
        Path staged = sessions.resolve("candidate.json.tmp");

        byte[] original = (
                "{\"cookies\":[{\"name\":\"access_token_web\",\"value\":\"still-good\"}],\"origins\":[]}"
        ).getBytes(StandardCharsets.UTF_8);

        Files.write(active, original);
        Files.writeString(staged, "{\"cookies\":[],\"origins\":[]}");

        try {
            manager.installStagedSession(4L, staged);
            fail("Cookie-empty replacement must be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("cookie-empty session state"));
        }

        assertArrayEquals(original, Files.readAllBytes(active));
        assertTrue(Files.exists(staged));
    }

    @Test
    public void successfulReplacementPreservesLastKnownGoodBackup()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("last-known-good").toPath();
        SessionManager manager = new SessionManager(sessions);
        Path active = manager.sessionFile(4L);
        Path staged = sessions.resolve("candidate.json.tmp");

        byte[] original = (
                "{\"cookies\":[{\"name\":\"session\",\"value\":\"old\"}],\"origins\":[]}"
        ).getBytes(StandardCharsets.UTF_8);
        byte[] replacement = (
                "{\"cookies\":[{\"name\":\"session\",\"value\":\"new\"}],\"origins\":[]}"
        ).getBytes(StandardCharsets.UTF_8);

        Files.write(active, original);
        Files.write(staged, replacement);

        manager.installStagedSession(4L, staged);

        assertArrayEquals(replacement, Files.readAllBytes(active));

        Path backups = sessions.resolve("backups");
        List<Path> files;
        try (var stream = Files.list(backups)) {
            files = stream.toList();
        }

        assertEquals(1, files.size());
        assertArrayEquals(original, Files.readAllBytes(files.getFirst()));
    }
}

package pl.flipbot.playwright.session;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SessionTempFileCleanerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void removesOnlyStaleStagedSessionFiles()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("sessions").toPath();
        Instant now = Instant.parse("2026-09-11T18:00:00Z");

        Path stale = sessions.resolve(".bot-4-123456.json.tmp");
        Path fresh = sessions.resolve(".bot-10-987654.json.tmp");
        Path active = sessions.resolve("bot-4.json");
        Path unrelated = sessions.resolve("notes.tmp");

        Files.writeString(stale, "stale");
        Files.writeString(fresh, "fresh");
        Files.writeString(active, "active");
        Files.writeString(unrelated, "unrelated");

        Files.setLastModifiedTime(
                stale,
                FileTime.from(now.minus(Duration.ofHours(2)))
        );
        Files.setLastModifiedTime(
                fresh,
                FileTime.from(now.minus(Duration.ofMinutes(10)))
        );

        int removed = SessionTempFileCleaner.cleanup(
                sessions,
                now,
                Duration.ofHours(1)
        );

        assertEquals(1, removed);
        assertFalse(Files.exists(stale));
        assertTrue(Files.exists(fresh));
        assertTrue(Files.exists(active));
        assertTrue(Files.exists(unrelated));
    }

    @Test
    public void missingSessionDirectoryIsANoop()
            throws Exception {
        Path missing = temporaryFolder.getRoot()
                .toPath()
                .resolve("missing");

        int removed = SessionTempFileCleaner.cleanup(
                missing,
                Instant.parse("2026-09-11T18:00:00Z"),
                Duration.ofHours(1)
        );

        assertEquals(0, removed);
        assertFalse(Files.exists(missing));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveStaleAge()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("invalid-age").toPath();

        SessionTempFileCleaner.cleanup(
                sessions,
                Instant.parse("2026-09-11T18:00:00Z"),
                Duration.ZERO
        );
    }
}

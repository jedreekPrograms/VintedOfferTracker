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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SessionManagerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void repositoryRootResolvesToPlaywrightModuleSessions()
            throws Exception {
        Path repositoryRoot = temporaryFolder.newFolder("repo-root").toPath();
        Path playwrightModule = createPlaywrightModule(repositoryRoot);

        Path resolved = SessionManager.resolveDefaultSessionDirectory(
                repositoryRoot,
                null
        );

        assertEquals(
                playwrightModule.resolve("sessions").toAbsolutePath().normalize(),
                resolved
        );
    }

    @Test
    public void moduleWorkingDirectoryResolvesToSameSessionDirectory()
            throws Exception {
        Path repositoryRoot = temporaryFolder.newFolder("module-cwd").toPath();
        Path playwrightModule = createPlaywrightModule(repositoryRoot);

        Path resolved = SessionManager.resolveDefaultSessionDirectory(
                playwrightModule,
                null
        );

        assertEquals(
                playwrightModule.resolve("sessions").toAbsolutePath().normalize(),
                resolved
        );
    }

    @Test
    public void nestedTargetWorkingDirectoryStillFindsModuleSessions()
            throws Exception {
        Path repositoryRoot = temporaryFolder.newFolder("target-cwd").toPath();
        Path playwrightModule = createPlaywrightModule(repositoryRoot);
        Path nestedWorkingDirectory = Files.createDirectories(
                playwrightModule.resolve("target/classes")
        );

        Path resolved = SessionManager.resolveDefaultSessionDirectory(
                nestedWorkingDirectory,
                null
        );

        assertEquals(
                playwrightModule.resolve("sessions").toAbsolutePath().normalize(),
                resolved
        );
    }

    @Test
    public void configuredSessionDirectoryOverridesDiscovery()
            throws Exception {
        Path repositoryRoot = temporaryFolder.newFolder("override-root").toPath();
        createPlaywrightModule(repositoryRoot);

        Path resolved = SessionManager.resolveDefaultSessionDirectory(
                repositoryRoot,
                "custom-sessions"
        );

        assertEquals(
                repositoryRoot.resolve("custom-sessions")
                        .toAbsolutePath()
                        .normalize(),
                resolved
        );
    }

    @Test
    public void zeroByteSessionIsNotConsideredUsable()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("zero-byte").toPath();
        SessionManager manager = new SessionManager(sessions);
        Files.createFile(manager.sessionFile(6L));

        assertFalse(manager.sessionExists(6L));
    }

    @Test
    public void validatedStagedSessionReplacesActiveSession()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("atomic-valid").toPath();
        SessionManager manager = new SessionManager(sessions);
        Path activeSession = manager.sessionFile(3L);
        Path stagedSession = sessions.resolve("staged.json.tmp");

        byte[] original = "{\"cookies\":[],\"origins\":[],\"marker\":\"old\"}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] replacement = "{\"cookies\":[],\"origins\":[],\"marker\":\"new\"}"
                .getBytes(StandardCharsets.UTF_8);

        Files.write(activeSession, original);
        Files.write(stagedSession, replacement);

        manager.installStagedSession(3L, stagedSession);

        assertArrayEquals(replacement, Files.readAllBytes(activeSession));
        assertFalse(Files.exists(stagedSession));
    }

    @Test
    public void emptyStagedSessionNeverTruncatesExistingActiveSession()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("atomic-empty").toPath();
        SessionManager manager = new SessionManager(sessions);
        Path activeSession = manager.sessionFile(3L);
        Path stagedSession = sessions.resolve("empty.json.tmp");

        byte[] original = "{\"cookies\":[{\"name\":\"session\"}],\"origins\":[]}"
                .getBytes(StandardCharsets.UTF_8);

        Files.write(activeSession, original);
        Files.createFile(stagedSession);

        try {
            manager.installStagedSession(3L, stagedSession);
            fail("Empty staged session should have been rejected");
        } catch (IllegalStateException expected) {
            // expected
        }

        assertArrayEquals(original, Files.readAllBytes(activeSession));
        assertTrue(Files.exists(stagedSession));
    }

    @Test
    public void malformedStagedSessionNeverReplacesExistingActiveSession()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("atomic-malformed").toPath();
        SessionManager manager = new SessionManager(sessions);
        Path activeSession = manager.sessionFile(3L);
        Path stagedSession = sessions.resolve("malformed.json.tmp");

        byte[] original = "{\"cookies\":[],\"origins\":[]}"
                .getBytes(StandardCharsets.UTF_8);

        Files.write(activeSession, original);
        Files.writeString(stagedSession, "{not-json");

        try {
            manager.installStagedSession(3L, stagedSession);
            fail("Malformed staged session should have been rejected");
        } catch (IllegalStateException expected) {
            // expected
        }

        assertArrayEquals(original, Files.readAllBytes(activeSession));
    }

    @Test
    public void invalidatingSessionBacksItUpWithoutRemovingActiveStorage()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("sessions").toPath();
        SessionManager manager = new SessionManager(sessions);
        Path activeSession = manager.sessionFile(3L);
        byte[] original = "{\"cookies\":[{\"name\":\"session\"}]}"
                .getBytes(StandardCharsets.UTF_8);

        Files.write(activeSession, original);

        manager.invalidateSession(3L);

        assertTrue(Files.exists(activeSession));
        assertArrayEquals(original, Files.readAllBytes(activeSession));

        Path backups = sessions.resolve("backups");
        assertTrue(Files.isDirectory(backups));

        List<Path> files;
        try (var stream = Files.list(backups)) {
            files = stream.toList();
        }

        assertEquals(1, files.size());
        assertTrue(files.getFirst().getFileName().toString().startsWith("bot-3-"));
        assertTrue(files.getFirst().getFileName().toString().endsWith(".json"));
        assertArrayEquals(original, Files.readAllBytes(files.getFirst()));
    }

    @Test
    public void repeatedRecoveryBackupsNeverMutateActiveSession()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("repeated-backups").toPath();
        SessionManager manager = new SessionManager(sessions);
        Path activeSession = manager.sessionFile(3L);
        byte[] original = "{\"cookies\":[{\"value\":\"still-valid\"}]}"
                .getBytes(StandardCharsets.UTF_8);

        Files.write(activeSession, original);

        manager.invalidateSession(3L);
        manager.invalidateSession(3L);

        assertTrue(Files.exists(activeSession));
        assertArrayEquals(original, Files.readAllBytes(activeSession));

        try (var stream = Files.list(sessions.resolve("backups"))) {
            assertEquals(2L, stream.count());
        }
    }

    @Test
    public void invalidatingMissingSessionDoesNotCreateBackupDirectory()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("missing-session").toPath();
        SessionManager manager = new SessionManager(sessions);

        manager.invalidateSession(3L);

        assertFalse(Files.exists(sessions.resolve("backups")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void sessionFileRejectsNonPositiveBotId() {
        SessionManager manager = new SessionManager(
                temporaryFolder.getRoot().toPath()
        );

        manager.sessionFile(0L);
    }

    private Path createPlaywrightModule(Path repositoryRoot)
            throws Exception {
        Path module = Files.createDirectories(
                repositoryRoot.resolve("playwright")
        );
        Files.createFile(module.resolve("pom.xml"));
        Files.createDirectories(
                module.resolve("src/main/java/pl/flipbot/playwright")
        );
        return module;
    }
}

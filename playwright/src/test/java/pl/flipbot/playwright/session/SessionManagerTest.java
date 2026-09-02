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

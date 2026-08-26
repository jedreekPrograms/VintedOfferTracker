package pl.flipbot.playwright.session;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Base64;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class SessionManagerTest {

    private static final String KEY_ONE = Base64.getEncoder().encodeToString(
            "11111111111111111111111111111111"
                    .getBytes(StandardCharsets.UTF_8)
    );

    private static final String KEY_TWO = Base64.getEncoder().encodeToString(
            "22222222222222222222222222222222"
                    .getBytes(StandardCharsets.UTF_8)
    );

    private static final String STORAGE_STATE = """
            {"cookies":[{"name":"auth_token","value":"super-secret-cookie"}],"origins":[]}
            """.trim();

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void persistsOnlyAuthenticatedCiphertextAndRoundTripsInMemory()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("sessions-roundtrip").toPath();
        SessionManager manager = new SessionManager(sessions, KEY_ONE);

        manager.saveSessionState(7L, STORAGE_STATE);

        Path encrypted = manager.encryptedSessionFile(7L);
        Path legacy = manager.legacySessionFile(7L);

        assertTrue(Files.exists(encrypted));
        assertFalse(Files.exists(legacy));

        String atRest = Files.readString(encrypted);
        assertTrue(atRest.startsWith("flipbot-session:v1:"));
        assertFalse(atRest.contains("super-secret-cookie"));
        assertFalse(atRest.contains("auth_token"));
        assertEquals(
                STORAGE_STATE,
                manager.loadSessionState(7L).orElseThrow()
        );

        PosixFileAttributeView directoryView = Files.getFileAttributeView(
                sessions,
                PosixFileAttributeView.class
        );
        PosixFileAttributeView fileView = Files.getFileAttributeView(
                encrypted,
                PosixFileAttributeView.class
        );

        if (directoryView != null && fileView != null) {
            assertEquals(
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE
                    ),
                    Files.getPosixFilePermissions(sessions)
            );
            assertEquals(
                    Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE
                    ),
                    Files.getPosixFilePermissions(encrypted)
            );
        }
    }

    @Test
    public void migratesLegacyPlaintextOnlyAfterEncryptedReplacementExists()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("sessions-legacy").toPath();
        SessionManager manager = new SessionManager(sessions, KEY_ONE);

        Files.writeString(
                manager.legacySessionFile(11L),
                STORAGE_STATE
        );

        assertEquals(
                STORAGE_STATE,
                manager.loadSessionState(11L).orElseThrow()
        );

        assertTrue(Files.exists(manager.encryptedSessionFile(11L)));
        assertFalse(Files.exists(manager.legacySessionFile(11L)));
        assertFalse(
                Files.readString(manager.encryptedSessionFile(11L))
                        .contains("super-secret-cookie")
        );
    }

    @Test
    public void wrongKeyFailsClosedAndLeavesCiphertextUntouched()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("sessions-wrong-key").toPath();
        SessionManager writer = new SessionManager(sessions, KEY_ONE);
        writer.saveSessionState(17L, STORAGE_STATE);

        Path encrypted = writer.encryptedSessionFile(17L);
        String before = Files.readString(encrypted);

        SessionManager wrongKeyReader = new SessionManager(sessions, KEY_TWO);

        assertThrows(
                IllegalStateException.class,
                () -> wrongKeyReader.loadSessionState(17L)
        );

        assertTrue(Files.exists(encrypted));
        assertEquals(before, Files.readString(encrypted));
    }

    @Test
    public void wrongKeyCannotOverwritePreviouslyValidCiphertext()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("sessions-overwrite-guard").toPath();
        SessionManager originalWriter = new SessionManager(sessions, KEY_ONE);
        originalWriter.saveSessionState(18L, STORAGE_STATE);

        Path encrypted = originalWriter.encryptedSessionFile(18L);
        String before = Files.readString(encrypted);

        SessionManager accidentalWriter = new SessionManager(sessions, KEY_TWO);

        assertThrows(
                IllegalStateException.class,
                () -> accidentalWriter.saveSessionState(
                        18L,
                        STORAGE_STATE.replace(
                                "super-secret-cookie",
                                "new-login-cookie"
                        )
                )
        );

        assertEquals(before, Files.readString(encrypted));
        assertEquals(
                STORAGE_STATE,
                originalWriter.loadSessionState(18L).orElseThrow()
        );
    }

    @Test
    public void ciphertextIsBoundToBotIdAndCannotBeSwappedBetweenAccounts()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("sessions-aad").toPath();
        SessionManager manager = new SessionManager(sessions, KEY_ONE);

        manager.saveSessionState(21L, STORAGE_STATE);
        manager.saveSessionState(
                22L,
                STORAGE_STATE.replace("super-secret-cookie", "other-cookie")
        );

        String bot21Ciphertext = Files.readString(
                manager.encryptedSessionFile(21L)
        );

        Files.writeString(
                manager.encryptedSessionFile(22L),
                bot21Ciphertext
        );

        assertThrows(
                IllegalStateException.class,
                () -> manager.loadSessionState(22L)
        );

        assertTrue(Files.exists(manager.encryptedSessionFile(22L)));
    }

    @Test
    public void removesOnlySessionsForBotsThatNoLongerExist()
            throws Exception {
        Path sessions = temporaryFolder.newFolder("sessions-orphans").toPath();
        SessionManager manager = new SessionManager(sessions, KEY_ONE);

        manager.saveSessionState(31L, STORAGE_STATE);
        manager.saveSessionState(32L, STORAGE_STATE);
        Files.writeString(
                manager.legacySessionFile(33L),
                STORAGE_STATE
        );

        int removed = manager.deleteSessionsForMissingBots(Set.of(32L));

        assertEquals(2, removed);
        assertFalse(Files.exists(manager.encryptedSessionFile(31L)));
        assertTrue(Files.exists(manager.encryptedSessionFile(32L)));
        assertFalse(Files.exists(manager.legacySessionFile(33L)));
    }
}

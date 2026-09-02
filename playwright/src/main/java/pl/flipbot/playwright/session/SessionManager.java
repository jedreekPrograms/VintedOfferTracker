package pl.flipbot.playwright.session;

import com.microsoft.playwright.BrowserContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class SessionManager {

    private static final Path DEFAULT_SESSION_DIRECTORY =
            Path.of("sessions");

    private static final String BACKUP_DIRECTORY_NAME =
            "backups";

    private static final DateTimeFormatter BACKUP_TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path sessionDirectory;

    public SessionManager() {
        this(DEFAULT_SESSION_DIRECTORY);
    }

    SessionManager(Path sessionDirectory) {
        this.sessionDirectory = sessionDirectory;

        try {
            Files.createDirectories(sessionDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not create Playwright session directory: "
                            + sessionDirectory,
                    exception
            );
        }
    }

    public boolean sessionExists(Long botId) {
        return Files.exists(sessionFile(botId));
    }

    public void saveSession(Long botId, BrowserContext context) {
        /*
         * Cookies and localStorage are sufficient for the Vinted session.
         * Persisting IndexedDB caused Playwright to save entries that could
         * later fail BrowserContext creation with "Unable to restore IndexedDB".
         */
        context.storageState(
                new BrowserContext.StorageStateOptions()
                        .setPath(sessionFile(botId))
        );
    }

    /**
     * Removes an unusable session from the active slot without destroying it.
     *
     * <p>A stored session may contain valid authentication cookies even when
     * Playwright cannot restore the complete storageState. Deleting that file
     * makes manual recovery impossible and can force an unnecessary login.
     * Instead, quarantine it under sessions/backups before a clean context is
     * allowed to continue.</p>
     *
     * <p>If preservation fails, this method deliberately throws. Continuing
     * with a clean context in that situation could later overwrite the only
     * copy of the user's session.</p>
     */
    public void invalidateSession(Long botId) {
        Path source = sessionFile(botId);

        if (!Files.exists(source)) {
            log.warn(
                    "[SESSION] Stored session for bot {} disappeared before it could be preserved: {}",
                    botId,
                    source
            );
            return;
        }

        Path backupDirectory = sessionDirectory.resolve(BACKUP_DIRECTORY_NAME);

        try {
            Files.createDirectories(backupDirectory);

            Path backup = nextBackupFile(botId, backupDirectory);
            movePreservingSession(source, backup);

            log.warn(
                    "[SESSION] Quarantined unusable stored session for bot {} instead of deleting it. backup={}",
                    botId,
                    backup
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not preserve stored session for bot " + botId
                            + "; refusing clean-context recovery so the original session is not lost.",
                    exception
            );
        }
    }

    private Path nextBackupFile(
            Long botId,
            Path backupDirectory
    ) {
        String timestamp = BACKUP_TIMESTAMP_FORMAT.format(LocalDateTime.now());
        String baseName = "bot-" + botId + "-" + timestamp;
        Path candidate = backupDirectory.resolve(baseName + ".json");

        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = backupDirectory.resolve(
                    baseName + "-" + suffix + ".json"
            );
            suffix++;
        }

        return candidate;
    }

    private void movePreservingSession(
            Path source,
            Path backup
    ) throws IOException {
        try {
            Files.move(
                    source,
                    backup,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, backup);
        }
    }

    public Path sessionFile(Long botId) {
        if (botId == null || botId <= 0) {
            throw new IllegalArgumentException(
                    "Bot ID must be positive"
            );
        }

        return sessionDirectory.resolve(
                "bot-" + botId + ".json"
        );
    }
}

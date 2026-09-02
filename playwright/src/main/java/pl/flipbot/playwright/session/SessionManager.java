package pl.flipbot.playwright.session;

import com.microsoft.playwright.BrowserContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
     * Preserves a recovery snapshot without ever removing the active session.
     *
     * <p>The historical method name is kept temporarily because the browser
     * context recovery path already calls it, but its contract is deliberately
     * non-destructive: sessions/bot-X.json remains exactly where it is.</p>
     *
     * <p>This matters especially for Vinted session-block cooldowns. A stored
     * session can still contain valid authentication cookies even if one
     * Playwright context restore fails. If a subsequent clean-context attempt
     * reaches a Vinted session-block page, the scheduler must only wait; it must
     * not destroy or quarantine the user's recoverable storageState.</p>
     *
     * <p>A timestamped copy is written under sessions/backups before recovery
     * continues. If the backup cannot be created, this method fails closed so
     * recovery cannot proceed without a preserved copy.</p>
     */
    public void invalidateSession(Long botId) {
        Path source = sessionFile(botId);

        if (!Files.exists(source)) {
            log.warn(
                    "[SESSION] Stored session for bot {} disappeared before a recovery backup could be created: {}",
                    botId,
                    source
            );
            return;
        }

        Path backupDirectory = sessionDirectory.resolve(BACKUP_DIRECTORY_NAME);

        try {
            Files.createDirectories(backupDirectory);

            Path backup = nextBackupFile(botId, backupDirectory);
            Files.copy(source, backup);

            log.warn(
                    "[SESSION] Preserved recovery backup for bot {} without removing the active session. active={}, backup={}",
                    botId,
                    source,
                    backup
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not back up stored session for bot " + botId
                            + "; refusing clean-context recovery so the active session is not put at risk.",
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

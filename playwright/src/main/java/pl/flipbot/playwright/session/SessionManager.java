package pl.flipbot.playwright.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.BrowserContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class SessionManager {

    private static final String SESSION_DIRECTORY_ENV =
            "FLIPBOT_SESSION_DIR";

    private static final String PLAYWRIGHT_MODULE_DIRECTORY =
            "playwright";

    private static final int MODULE_DISCOVERY_MAX_DEPTH = 6;

    private static final AtomicBoolean SESSION_DIRECTORY_LOGGED =
            new AtomicBoolean();

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    private static final Path DEFAULT_SESSION_DIRECTORY =
            resolveDefaultSessionDirectory(
                    Path.of(System.getProperty("user.dir", ".")),
                    System.getenv(SESSION_DIRECTORY_ENV)
            );

    private static final String BACKUP_DIRECTORY_NAME =
            "backups";

    private final Path sessionDirectory;

    public SessionManager() {
        this(DEFAULT_SESSION_DIRECTORY);
    }

    SessionManager(Path sessionDirectory) {
        this.sessionDirectory = sessionDirectory
                .toAbsolutePath()
                .normalize();

        try {
            Files.createDirectories(this.sessionDirectory);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not create Playwright session directory: "
                            + this.sessionDirectory,
                    exception
            );
        }

        if (SESSION_DIRECTORY_LOGGED.compareAndSet(false, true)) {
            log.info(
                    "[SESSION] Using Playwright session directory: {}. workingDirectory={}, overrideEnv={}",
                    this.sessionDirectory,
                    Path.of(System.getProperty("user.dir", "."))
                            .toAbsolutePath()
                            .normalize(),
                    System.getenv(SESSION_DIRECTORY_ENV) == null
                            ? "<not set>"
                            : SESSION_DIRECTORY_ENV
            );
        }
    }

    static Path resolveDefaultSessionDirectory(
            Path workingDirectory,
            String configuredDirectory
    ) {
        Path normalizedWorkingDirectory = workingDirectory
                .toAbsolutePath()
                .normalize();

        if (configuredDirectory != null
                && !configuredDirectory.isBlank()) {
            Path configured = Path.of(configuredDirectory.trim());
            if (!configured.isAbsolute()) {
                configured = normalizedWorkingDirectory.resolve(configured);
            }
            return configured.toAbsolutePath().normalize();
        }

        Path moduleDirectory = findPlaywrightModuleDirectory(
                normalizedWorkingDirectory
        );

        if (moduleDirectory != null) {
            return moduleDirectory.resolve("sessions")
                    .toAbsolutePath()
                    .normalize();
        }

        return normalizedWorkingDirectory.resolve("sessions")
                .toAbsolutePath()
                .normalize();
    }

    private static Path findPlaywrightModuleDirectory(
            Path workingDirectory
    ) {
        Path current = workingDirectory;

        for (int depth = 0;
             current != null && depth <= MODULE_DISCOVERY_MAX_DEPTH;
             depth++, current = current.getParent()) {
            if (looksLikePlaywrightModule(current)) {
                return current;
            }

            Path nestedModule = current.resolve(
                    PLAYWRIGHT_MODULE_DIRECTORY
            );
            if (looksLikePlaywrightModule(nestedModule)) {
                return nestedModule;
            }
        }

        return null;
    }

    private static boolean looksLikePlaywrightModule(Path candidate) {
        return Files.isRegularFile(candidate.resolve("pom.xml"))
                && Files.isDirectory(
                        candidate.resolve(
                                "src/main/java/pl/flipbot/playwright"
                        )
                );
    }

    public boolean sessionExists(Long botId) {
        Path session = sessionFile(botId);

        if (!Files.exists(session)) {
            return false;
        }

        try {
            if (!Files.isRegularFile(session) || Files.size(session) == 0) {
                throw new IllegalStateException(
                        "Stored session for bot " + botId
                                + " exists but is empty or is not a regular file: "
                                + session
                                + ". Refusing to treat it as a missing session because that could silently start a clean browser context."
                );
            }

            JsonNode root = OBJECT_MAPPER.readTree(session.toFile());
            SessionSnapshotValidator.validateShape(botId, root);
            return true;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not validate stored session for bot " + botId
                            + ": " + session
                            + ". Refusing clean-context fallback.",
                    exception
            );
        }
    }

    public void saveSession(Long botId, BrowserContext context) {
        Path stagedSession;
        try {
            stagedSession = Files.createTempFile(
                    sessionDirectory,
                    ".bot-" + botId + "-",
                    ".json.tmp"
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not create a staging file for bot " + botId
                            + " session; the active session was left untouched.",
                    exception
            );
        }

        try {
            context.storageState(
                    new BrowserContext.StorageStateOptions()
                            .setPath(stagedSession)
            );

            installStagedSession(botId, stagedSession);
        } finally {
            deleteQuietly(stagedSession);
        }
    }

    void installStagedSession(
            Long botId,
            Path stagedSession
    ) {
        Path activeSession = sessionFile(botId);
        JsonNode stagedRoot = validateStagedSession(botId, stagedSession);
        JsonNode activeRoot = readActiveSessionForReplacement(
                botId,
                activeSession
        );

        SessionSnapshotValidator.validateDoesNotLoseEstablishedCookies(
                botId,
                activeRoot,
                stagedRoot
        );

        if (activeRoot != null) {
            preserveRotatingBackup(botId, activeSession, "last-known-good");
        }

        try {
            try {
                Files.move(
                        stagedSession,
                        activeSession,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(
                        stagedSession,
                        activeSession,
                        StandardCopyOption.REPLACE_EXISTING
                );
            }

            log.debug(
                    "[SESSION] Installed validated session state for bot {}: {}",
                    botId,
                    activeSession
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not install validated session state for bot " + botId
                            + "; the previous active session was preserved whenever the filesystem allowed it.",
                    exception
            );
        }
    }

    private JsonNode validateStagedSession(
            Long botId,
            Path stagedSession
    ) {
        try {
            if (!Files.isRegularFile(stagedSession)
                    || Files.size(stagedSession) == 0) {
                throw new IllegalStateException(
                        "Playwright produced an empty session state for bot "
                                + botId
                                + "; refusing to replace the active session."
                );
            }

            JsonNode root = OBJECT_MAPPER.readTree(stagedSession.toFile());
            SessionSnapshotValidator.validateShape(botId, root);
            return root;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not validate staged session state for bot " + botId
                            + "; refusing to replace the active session.",
                    exception
            );
        }
    }

    private JsonNode readActiveSessionForReplacement(
            Long botId,
            Path activeSession
    ) {
        if (!Files.exists(activeSession)) {
            return null;
        }

        try {
            if (!Files.isRegularFile(activeSession)
                    || Files.size(activeSession) == 0) {
                throw new IllegalStateException(
                        "Active session for bot " + botId
                                + " is empty or is not a regular file: "
                                + activeSession
                                + ". Refusing to replace or silently recover it."
                );
            }

            JsonNode root = OBJECT_MAPPER.readTree(activeSession.toFile());
            SessionSnapshotValidator.validateShape(botId, root);
            return root;
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read the active session for bot " + botId
                            + "; refusing to replace the last-known-good file.",
                    exception
            );
        }
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            log.warn(
                    "[SESSION] Could not remove temporary staged session file: {}",
                    path,
                    exception
            );
        }
    }

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

        preserveRotatingBackup(botId, source, "recovery");
    }

    private void preserveRotatingBackup(
            Long botId,
            Path source,
            String reason
    ) {
        Path backupDirectory = sessionDirectory.resolve(BACKUP_DIRECTORY_NAME);

        try {
            Files.createDirectories(backupDirectory);

            Path backup = backupDirectory.resolve(
                    "bot-" + botId + "-" + reason + ".json"
            );
            Files.copy(
                    source,
                    backup,
                    StandardCopyOption.REPLACE_EXISTING
            );

            log.debug(
                    "[SESSION] Refreshed {} backup for bot {} without removing the active session. active={}, backup={}",
                    reason,
                    botId,
                    source,
                    backup
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not back up stored session for bot " + botId
                            + "; refusing session replacement/recovery so the active session is not put at risk.",
                    exception
            );
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

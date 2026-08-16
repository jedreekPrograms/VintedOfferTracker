package pl.flipbot.playwright.session;

import com.microsoft.playwright.BrowserContext;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class SessionManager {

    private static final Path SESSION_DIRECTORY =
            Path.of("sessions");


    public boolean sessionExists(Long botId) {

        return Files.exists(
                sessionFile(botId)
        );

    }

    public SessionManager() {

        try {

            Files.createDirectories(SESSION_DIRECTORY);

        } catch (IOException e) {

            throw new RuntimeException(e);

        }
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

    public void invalidateSession(Long botId) {
        Path path = sessionFile(botId);

        try {
            if (Files.deleteIfExists(path)) {
                log.warn(
                        "[SESSION] Removed unusable stored session for bot {}: {}",
                        botId,
                        path
                );
            }
        } catch (IOException exception) {
            /*
             * Recovery may still proceed with a clean BrowserContext. If login
             * succeeds, the next saveSession() overwrites the old state file.
             */
            log.warn(
                    "[SESSION] Could not delete unusable stored session for bot {}. "
                            + "Continuing with a clean context; a successful session save will replace it.",
                    botId,
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

        return SESSION_DIRECTORY.resolve(
                "bot-" + botId + ".json"
        );
    }
}

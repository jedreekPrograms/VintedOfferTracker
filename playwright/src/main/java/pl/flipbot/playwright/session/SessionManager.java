package pl.flipbot.playwright.session;

import com.microsoft.playwright.BrowserContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

        context.storageState(
                new BrowserContext.StorageStateOptions()
                        .setIndexedDB(true)
                        .setPath(sessionFile(botId))
        );
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

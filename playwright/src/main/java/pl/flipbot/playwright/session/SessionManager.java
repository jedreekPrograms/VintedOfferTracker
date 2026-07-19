package pl.flipbot.playwright.session;

import com.microsoft.playwright.BrowserContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SessionManager {

    private static final Path SESSION_DIRECTORY =
            Path.of("sessions");


    public boolean sessionExists(String email) {

        return Files.exists(
                sessionFile(email)
        );

    }

    public SessionManager() {

        try {

            Files.createDirectories(SESSION_DIRECTORY);

        } catch (IOException e) {

            throw new RuntimeException(e);

        }
    }

    public void saveSession(String email, BrowserContext context) {

        context.storageState(
                new BrowserContext.StorageStateOptions()
                        .setPath(sessionFile(email))
        );
    }

    public Path sessionFile(String email) {

        String safeName = email
                .replace("@", "_")
                .replace(".", "_");

        return SESSION_DIRECTORY.resolve(
                safeName + ".json"
        );
    }
}

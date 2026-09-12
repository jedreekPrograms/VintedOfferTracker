package pl.flipbot.playwright.session;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

@Slf4j
public final class SessionTempFileCleaner {

    private static final String SESSION_DIRECTORY_ENV = "FLIPBOT_SESSION_DIR";
    private static final Duration STALE_AFTER = Duration.ofHours(1);
    private static final String STAGED_SESSION_GLOB = ".bot-*.json.tmp";

    private SessionTempFileCleaner() {
    }

    public static void cleanupDefaultDirectory() {
        Path sessionDirectory = SessionManager.resolveDefaultSessionDirectory(
                Path.of(System.getProperty("user.dir", ".")),
                System.getenv(SESSION_DIRECTORY_ENV)
        );

        int removed = cleanup(
                sessionDirectory,
                Instant.now(),
                STALE_AFTER
        );

        if (removed > 0) {
            log.info(
                    "[SESSION] Removed {} stale staged session file(s) from {}.",
                    removed,
                    sessionDirectory
            );
        }
    }

    static int cleanup(
            Path sessionDirectory,
            Instant now,
            Duration staleAfter
    ) {
        if (sessionDirectory == null
                || now == null
                || staleAfter == null
                || staleAfter.isNegative()
                || staleAfter.isZero()) {
            throw new IllegalArgumentException(
                    "Session directory, current time and positive stale age are required."
            );
        }

        if (!Files.isDirectory(sessionDirectory)) {
            return 0;
        }

        Instant cutoff = now.minus(staleAfter);
        int removed = 0;

        try (DirectoryStream<Path> stagedFiles = Files.newDirectoryStream(
                sessionDirectory,
                STAGED_SESSION_GLOB
        )) {
            for (Path stagedFile : stagedFiles) {
                try {
                    if (!Files.isRegularFile(stagedFile)) {
                        continue;
                    }

                    FileTime lastModified = Files.getLastModifiedTime(stagedFile);
                    if (lastModified.toInstant().isAfter(cutoff)) {
                        continue;
                    }

                    if (Files.deleteIfExists(stagedFile)) {
                        removed++;
                        log.debug(
                                "[SESSION] Removed stale staged session file: {}",
                                stagedFile
                        );
                    }
                } catch (IOException exception) {
                    log.warn(
                            "[SESSION] Could not inspect/remove stale staged session file: {}",
                            stagedFile,
                            exception
                    );
                }
            }
        } catch (IOException exception) {
            log.warn(
                    "[SESSION] Could not scan session directory for stale staged files: {}",
                    sessionDirectory,
                    exception
            );
        }

        return removed;
    }
}

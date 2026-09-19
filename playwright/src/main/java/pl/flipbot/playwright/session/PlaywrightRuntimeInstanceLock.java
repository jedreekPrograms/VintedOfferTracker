package pl.flipbot.playwright.session;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Prevents two FlipBot Playwright JVMs from scheduling the same bots against
 * the same persisted session directory at the same time.
 *
 * <p>The scheduler already serializes jobs for one bot inside a single process,
 * but an accidental second JVM would otherwise have its own scheduler and could
 * concurrently replace the same bot-X.json file. The operating-system file lock
 * is held for the lifetime of the runtime and is released automatically if the
 * process exits.</p>
 */
@Slf4j
public final class PlaywrightRuntimeInstanceLock implements AutoCloseable {

    private final Path path;
    private final FileChannel channel;
    private final FileLock lock;

    private PlaywrightRuntimeInstanceLock(
            Path path,
            FileChannel channel,
            FileLock lock
    ) {
        this.path = path;
        this.channel = channel;
        this.lock = lock;
    }

    public static PlaywrightRuntimeInstanceLock acquireDefault() {
        SessionManager sessionManager = new SessionManager();
        return acquire(sessionManager.runtimeLockFile());
    }

    static PlaywrightRuntimeInstanceLock acquire(Path path) {
        Path normalized = path.toAbsolutePath().normalize();

        try {
            Files.createDirectories(normalized.getParent());

            FileChannel channel = FileChannel.open(
                    normalized,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE
            );

            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException exception) {
                channel.close();
                throw alreadyRunning(normalized, exception);
            }

            if (lock == null) {
                channel.close();
                throw alreadyRunning(normalized, null);
            }

            writeOwnerMetadata(channel);

            log.info(
                    "[SESSION] Acquired exclusive FlipBot Playwright runtime lock: {}",
                    normalized
            );

            return new PlaywrightRuntimeInstanceLock(
                    normalized,
                    channel,
                    lock
            );
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not acquire the FlipBot Playwright runtime lock at "
                            + normalized
                            + ". Refusing to start because concurrent session writers cannot be ruled out.",
                    exception
            );
        }
    }

    private static IllegalStateException alreadyRunning(
            Path path,
            Exception cause
    ) {
        return new IllegalStateException(
                "Another FlipBot Playwright runtime already owns session directory "
                        + path.getParent()
                        + ". Stop the other Playwright JVM before starting a second one.",
                cause
        );
    }

    private static void writeOwnerMetadata(FileChannel channel)
            throws IOException {
        String metadata =
                "pid=" + ProcessHandle.current().pid()
                        + System.lineSeparator();

        channel.truncate(0L);
        channel.position(0L);
        channel.write(
                ByteBuffer.wrap(
                        metadata.getBytes(StandardCharsets.UTF_8)
                )
        );
        channel.force(true);
    }

    @Override
    public void close() {
        try {
            if (lock.isValid()) {
                lock.release();
            }
        } catch (IOException exception) {
            log.warn(
                    "[SESSION] Could not release FlipBot Playwright runtime lock cleanly: {}",
                    path,
                    exception
            );
        } finally {
            try {
                channel.close();
            } catch (IOException exception) {
                log.warn(
                        "[SESSION] Could not close FlipBot Playwright runtime-lock channel: {}",
                        path,
                        exception
                );
            }
        }
    }
}

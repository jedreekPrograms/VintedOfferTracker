package pl.flipbot.playwright.session;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Path;

import static org.junit.Assert.assertThrows;

public class PlaywrightRuntimeInstanceLockTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void secondRuntimeCannotOwnTheSameSessionDirectory()
            throws Exception {
        Path lockFile = temporaryFolder.newFolder("runtime-lock")
                .toPath()
                .resolve(".flipbot-playwright-runtime.lock");

        try (PlaywrightRuntimeInstanceLock first =
                     PlaywrightRuntimeInstanceLock.acquire(lockFile)) {

            assertThrows(
                    IllegalStateException.class,
                    () -> PlaywrightRuntimeInstanceLock.acquire(lockFile)
            );
        }

        try (PlaywrightRuntimeInstanceLock afterRelease =
                     PlaywrightRuntimeInstanceLock.acquire(lockFile)) {
            // Acquiring again after the first owner closed proves the lock is
            // tied to the runtime lifetime rather than the existence of the file.
        }
    }
}

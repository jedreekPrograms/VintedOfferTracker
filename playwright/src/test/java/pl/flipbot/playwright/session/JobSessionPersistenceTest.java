package pl.flipbot.playwright.session;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotDetailsDto;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class JobSessionPersistenceTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void unsafeJobEndPreservesFreshCheckpointInsteadOfOlderRotatingBackup() throws Exception {
        Fixture f = new Fixture(folder.newFolder().toPath());
        when(f.page.url()).thenReturn("https://www.vinted.pl/session-refresh");

        new JobSessionPersistence().finish(f.context, true, true, true);

        assertEquals(snapshot("fresh-pre-job-token"), Files.readString(f.manager.sessionFile(4L)));
        assertEquals(snapshot("previous-job-token"), Files.readString(f.backup));
        verify(f.context, never()).saveSession();
    }

    @Test
    public void healthyJobEndStillSavesRotatedSessionAndPreservesBackup() throws Exception {
        Fixture f = new Fixture(folder.newFolder().toPath());
        when(f.page.url()).thenReturn("https://www.vinted.pl/inbox");
        Locator authenticated = mock(Locator.class);
        when(f.page.locator("[data-testid='header-conversations-button']")).thenReturn(authenticated);
        when(authenticated.count()).thenReturn(1);
        when(authenticated.nth(0)).thenReturn(authenticated);
        when(authenticated.isVisible()).thenReturn(true);

        new JobSessionPersistence().finish(f.context, true, true, true);

        assertEquals(snapshot("after-job-token"), Files.readString(f.manager.sessionFile(4L)));
        assertEquals(snapshot("fresh-pre-job-token"), Files.readString(f.backup));
        verify(f.context).saveSession();
    }

    @Test
    public void failedJobDoesNotSaveOrRollBackItsAuthenticatedCheckpoint() throws Exception {
        Fixture f = new Fixture(folder.newFolder().toPath());

        new JobSessionPersistence().finish(f.context, true, false, true);

        assertEquals(snapshot("fresh-pre-job-token"), Files.readString(f.manager.sessionFile(4L)));
        verify(f.context, never()).saveSession();
        verifyNoInteractions(f.page);
    }

    @Test
    public void healthProbeFailureDoesNotOverwriteSessionOrPreventCallerCleanup() throws Exception {
        Fixture f = new Fixture(folder.newFolder().toPath());
        when(f.page.url()).thenThrow(new PlaywrightException("Page closed during final inspection"));

        new JobSessionPersistence().finish(f.context, true, true, true);

        assertEquals(snapshot("fresh-pre-job-token"), Files.readString(f.manager.sessionFile(4L)));
        verify(f.context, never()).saveSession();
    }

    @Test
    public void incompleteLoginNeverSavesTheChallengePage() throws Exception {
        Fixture f = new Fixture(folder.newFolder().toPath());

        new JobSessionPersistence().finish(f.context, false, false, false);

        assertEquals(snapshot("fresh-pre-job-token"), Files.readString(f.manager.sessionFile(4L)));
        verify(f.context, never()).saveSession();
        verifyNoInteractions(f.page);
    }

    private static String snapshot(String token) {
        return "{\"cookies\":[{\"name\":\"access_token_web\",\"value\":\"" + token + "\"}],\"origins\":[]}";
    }

    private static final class Fixture {
        final BotContext context = mock(BotContext.class);
        final Page page = mock(Page.class);
        final SessionManager manager;
        final Path backup;

        Fixture(Path directory) throws Exception {
            manager = new SessionManager(directory);
            Files.writeString(manager.sessionFile(4L), snapshot("previous-job-token"));
            Path checkpoint = directory.resolve("checkpoint.tmp");
            Files.writeString(checkpoint, snapshot("fresh-pre-job-token"));
            manager.installStagedSession(4L, checkpoint);
            backup = directory.resolve("backups/bot-4-last-known-good.json");
            assertEquals(snapshot("previous-job-token"), Files.readString(backup));
            BotDetailsDto bot = new BotDetailsDto();
            bot.setId(4L);
            when(context.getBot()).thenReturn(bot);
            when(context.getPage()).thenReturn(page);
            when(context.getSessionManager()).thenReturn(manager);
            doAnswer(call -> {
                Path candidate = directory.resolve("post-job.tmp");
                Files.writeString(candidate, snapshot("after-job-token"));
                manager.installStagedSession(4L, candidate);
                return null;
            }).when(context).saveSession();
        }
    }
}

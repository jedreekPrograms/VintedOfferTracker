package pl.flipbot.playwright.worker;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorkerBrowserRetentionPolicyTest {

    @Test
    public void headlessWorkerKeepsBrowserRuntimeForThroughput() {
        assertTrue(
                WorkerBrowserRetentionPolicy.keepBrowserOpenBetweenJobs(true)
        );
    }

    @Test
    public void headfulWorkerReleasesBrowserRuntimeAfterJob() {
        assertFalse(
                WorkerBrowserRetentionPolicy.keepBrowserOpenBetweenJobs(false)
        );
    }
}

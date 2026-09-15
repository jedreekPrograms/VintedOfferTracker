package pl.flipbot.playwright.worker;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertEquals;

public class WorkerBrowserRetentionPolicyTest {

    @Test
    public void headlessWorkerClosesBrowserRuntimeAndDropsReference() {
        Object runtime = new Object();
        AtomicInteger closeCalls = new AtomicInteger();

        Object result = WorkerBrowserRetentionPolicy.afterJob(
                runtime,
                true,
                ignored -> closeCalls.incrementAndGet()
        );

        assertNull(result);
        assertEquals(1, closeCalls.get());
        assertFalse(
                WorkerBrowserRetentionPolicy.keepBrowserOpenBetweenJobs(true)
        );
    }

    @Test
    public void headfulWorkerClosesBrowserRuntimeAndDropsReference() {
        Object runtime = new Object();
        AtomicInteger closeCalls = new AtomicInteger();

        Object result = WorkerBrowserRetentionPolicy.afterJob(
                runtime,
                false,
                ignored -> closeCalls.incrementAndGet()
        );

        assertNull(result);
        assertEquals(1, closeCalls.get());
        assertFalse(
                WorkerBrowserRetentionPolicy.keepBrowserOpenBetweenJobs(false)
        );
    }

    @Test
    public void missingBrowserRuntimeDoesNotInvokeCloseAction() {
        AtomicInteger closeCalls = new AtomicInteger();

        Object result = WorkerBrowserRetentionPolicy.afterJob(
                null,
                true,
                ignored -> closeCalls.incrementAndGet()
        );

        assertNull(result);
        assertEquals(0, closeCalls.get());
    }
}

package pl.flipbot.playwright.worker;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class WorkerBrowserRetentionPolicyTest {

    @Test
    public void headlessWorkerKeepsBrowserForImmediatelyReadyWork() {
        Object runtime = new Object();
        AtomicInteger closeCalls = new AtomicInteger();

        Object result = WorkerBrowserRetentionPolicy.afterJob(
                runtime,
                true,
                true,
                ignored -> closeCalls.incrementAndGet()
        );

        assertSame(runtime, result);
        assertEquals(0, closeCalls.get());
        assertTrue(
                WorkerBrowserRetentionPolicy.keepBrowserOpenBetweenJobs(true)
        );
    }

    @Test
    public void headlessWorkerClosesBrowserWhenReadyQueueIsDrained() {
        Object runtime = new Object();
        AtomicInteger closeCalls = new AtomicInteger();

        Object result = WorkerBrowserRetentionPolicy.afterJob(
                runtime,
                true,
                false,
                ignored -> closeCalls.incrementAndGet()
        );

        assertNull(result);
        assertEquals(1, closeCalls.get());
    }

    @Test
    public void headfulWorkerClosesBrowserEvenWhenWorkIsReady() {
        Object runtime = new Object();
        AtomicInteger closeCalls = new AtomicInteger();

        Object result = WorkerBrowserRetentionPolicy.afterJob(
                runtime,
                false,
                true,
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
                true,
                ignored -> closeCalls.incrementAndGet()
        );

        assertNull(result);
        assertEquals(0, closeCalls.get());
    }
}

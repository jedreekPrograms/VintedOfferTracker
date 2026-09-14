package pl.flipbot.playwright.worker;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class WorkerBrowserRetentionPolicyTest {

    @Test
    public void headlessWorkerKeepsSameBrowserRuntimeWithoutClosingIt() {
        Object runtime = new Object();
        AtomicInteger closeCalls = new AtomicInteger();

        Object result = WorkerBrowserRetentionPolicy.afterJob(
                runtime,
                true,
                ignored -> closeCalls.incrementAndGet()
        );

        assertSame(runtime, result);
        org.junit.Assert.assertEquals(0, closeCalls.get());
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
        org.junit.Assert.assertEquals(1, closeCalls.get());
    }

    @Test
    public void missingBrowserRuntimeDoesNotInvokeCloseAction() {
        AtomicInteger closeCalls = new AtomicInteger();

        Object result = WorkerBrowserRetentionPolicy.afterJob(
                null,
                false,
                ignored -> closeCalls.incrementAndGet()
        );

        assertNull(result);
        org.junit.Assert.assertEquals(0, closeCalls.get());
    }
}

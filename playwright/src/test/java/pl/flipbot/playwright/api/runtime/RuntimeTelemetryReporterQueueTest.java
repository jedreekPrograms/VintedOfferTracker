package pl.flipbot.playwright.api.runtime;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RuntimeTelemetryReporterQueueTest {

    @Test
    public void queueHasHardBoundAndRejectsInsteadOfGrowingWithoutLimit()
            throws Exception {
        ThreadPoolExecutor executor = RuntimeTelemetryReporter.createExecutor();
        CountDownLatch runningTaskStarted = new CountDownLatch(1);
        CountDownLatch releaseRunningTask = new CountDownLatch(1);

        try {
            executor.execute(() -> {
                runningTaskStarted.countDown();
                try {
                    releaseRunningTask.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(
                    "telemetry worker should start the blocking task",
                    runningTaskStarted.await(1, TimeUnit.SECONDS)
            );

            int capacity = RuntimeTelemetryReporter.maxPendingEvents();
            for (int index = 0; index < capacity; index++) {
                executor.execute(() -> { });
            }

            assertEquals(capacity, executor.getQueue().size());

            try {
                executor.execute(() -> { });
                fail("Expected telemetry executor to reject work above its hard queue limit");
            } catch (RejectedExecutionException expected) {
                assertEquals(capacity, executor.getQueue().size());
            }
        } finally {
            releaseRunningTask.countDown();
            executor.shutdownNow();
            executor.awaitTermination(1, TimeUnit.SECONDS);
        }
    }
}

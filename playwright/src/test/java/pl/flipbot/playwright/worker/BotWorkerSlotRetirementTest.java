package pl.flipbot.playwright.worker;

import org.junit.Test;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BotWorkerSlotRetirementTest {

    @Test
    public void idleSlotStopsAfterGracefulRetirementRequest() throws Exception {
        CountDownLatch pollEntered = new CountDownLatch(1);
        RuntimeTelemetryReporter telemetry = new RuntimeTelemetryReporter();
        BotRunScheduler scheduler = new PollOnlyScheduler(
                config(),
                telemetry,
                pollEntered
        );
        BotWorkerSlot slot = new BotWorkerSlot(
                7,
                scheduler,
                config(),
                telemetry
        );
        Thread thread = new Thread(slot, "retirement-test-slot");

        try {
            thread.start();

            assertTrue(
                    "worker should enter scheduler polling",
                    pollEntered.await(1, TimeUnit.SECONDS)
            );

            assertTrue(slot.requestRetirement());
            assertFalse(
                    "retirement request must be idempotent",
                    slot.requestRetirement()
            );

            thread.join(2_500L);

            assertFalse(
                    "idle worker should stop promptly after retirement",
                    thread.isAlive()
            );
            assertTrue(slot.isRetirementRequested());
        } finally {
            thread.interrupt();
            thread.join(1_000L);
            scheduler.shutdown();
            telemetry.close();
        }
    }

    private WorkerRuntimeConfig config() {
        return new WorkerRuntimeConfig(
                10,
                5L,
                120L,
                900L,
                60L,
                60L,
                600L,
                180L,
                30L,
                true
        );
    }

    private static final class PollOnlyScheduler extends BotRunScheduler {

        private final CountDownLatch pollEntered;

        private PollOnlyScheduler(
                WorkerRuntimeConfig config,
                RuntimeTelemetryReporter telemetryReporter,
                CountDownLatch pollEntered
        ) {
            super(config, telemetryReporter);
            this.pollEntered = pollEntered;
        }

        @Override
        public ScheduledBotTask pollNext(long timeoutMillis)
                throws InterruptedException {
            pollEntered.countDown();
            Thread.sleep(Math.min(50L, Math.max(1L, timeoutMillis)));
            return null;
        }
    }
}

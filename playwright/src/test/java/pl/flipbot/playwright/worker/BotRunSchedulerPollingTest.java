package pl.flipbot.playwright.worker;

import org.junit.Test;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class BotRunSchedulerPollingTest {

    @Test
    public void pollReturnsReadyCurrentTask() throws Exception {
        NoOpTelemetryReporter telemetry = new NoOpTelemetryReporter();
        BotRunScheduler scheduler = new BotRunScheduler(config(), telemetry);

        try {
            scheduler.reconcileRunningBots(Map.of(1L, true));

            ScheduledBotTask task = scheduler.pollNext(100L);

            assertNotNull(task);
            assertEquals(Long.valueOf(1L), task.botId());
            assertEquals(ScheduledJobType.NEGOTIATION_CHECK, task.jobType());
        } finally {
            scheduler.shutdown();
            telemetry.close();
        }
    }

    @Test
    public void pollTimesOutWhileAllFutureJobsAreStillDelayed() throws Exception {
        NoOpTelemetryReporter telemetry = new NoOpTelemetryReporter();
        BotRunScheduler scheduler = new BotRunScheduler(config(), telemetry);

        try {
            scheduler.reconcileRunningBots(Map.of(1L, true));
            ScheduledBotTask first = scheduler.takeNext();

            scheduler.completeRun(
                    first.botId(),
                    first.jobType(),
                    500L,
                    true,
                    false
            );

            assertNull(scheduler.pollNext(30L));
        } finally {
            scheduler.shutdown();
            telemetry.close();
        }
    }

    private WorkerRuntimeConfig config() {
        return new WorkerRuntimeConfig(
                1,
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

    private static final class NoOpTelemetryReporter
            extends RuntimeTelemetryReporter {

        @Override
        public void queued(Long botId, long nextRunAtEpochMs) {
            // Unit test: scheduler queue semantics only.
        }

        @Override
        public void idle(Long botId) {
            // Unit test: scheduler queue semantics only.
        }
    }
}

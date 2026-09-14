package pl.flipbot.playwright.worker;

import org.junit.Test;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.negotiation.ConsecutiveContactUnavailableTracker;

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

    @Test
    public void stoppingQueuedBotClearsEphemeralStateImmediately() {
        long botId = 901L;
        NoOpTelemetryReporter telemetry = new NoOpTelemetryReporter();
        BotRunScheduler scheduler = new BotRunScheduler(config(), telemetry);
        ConsecutiveContactUnavailableTracker tracker =
                new ConsecutiveContactUnavailableTracker();

        try {
            scheduler.reconcileRunningBots(Map.of(botId, false));
            tracker.recordSuspected(botId, "listing-A");
            tracker.recordSuspected(botId, "listing-A");

            scheduler.reconcileRunningBots(Map.of());

            assertEquals(1, tracker.recordSuspected(botId, "listing-A"));
            assertEquals(0, scheduler.enabledBotCount());
        } finally {
            ConsecutiveContactUnavailableTracker.clearBot(botId);
            scheduler.shutdown();
            telemetry.close();
        }
    }

    @Test
    public void stoppingWorkingBotDefersCleanupUntilClaimedJobCompletes()
            throws Exception {
        long botId = 902L;
        NoOpTelemetryReporter telemetry = new NoOpTelemetryReporter();
        BotRunScheduler scheduler = new BotRunScheduler(config(), telemetry);
        ConsecutiveContactUnavailableTracker tracker =
                new ConsecutiveContactUnavailableTracker();

        try {
            scheduler.reconcileRunningBots(Map.of(botId, false));
            ScheduledBotTask claimed = scheduler.pollNext(100L);
            assertNotNull(claimed);

            tracker.recordSuspected(botId, "listing-A");
            tracker.recordSuspected(botId, "listing-A");

            scheduler.reconcileRunningBots(Map.of());

            // The claimed job is still allowed to finish. Its process-local
            // state must not be cleared underneath it.
            assertEquals(3, tracker.recordSuspected(botId, "listing-A"));

            scheduler.completeRun(
                    claimed.botId(),
                    claimed.jobType(),
                    0L,
                    false,
                    false
            );

            assertEquals(1, tracker.recordSuspected(botId, "listing-A"));
            assertEquals(0, scheduler.enabledBotCount());
        } finally {
            ConsecutiveContactUnavailableTracker.clearBot(botId);
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

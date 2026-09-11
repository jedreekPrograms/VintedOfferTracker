package pl.flipbot.playwright.worker;

import org.junit.Test;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class BotRunSchedulerCaptchaTest {

    @Test
    public void pausedBotOnlyQueuesRecoveryAfterExplicitRequest()
            throws Exception {
        try (SilentTelemetryReporter telemetry =
                     new SilentTelemetryReporter()) {
            BotRunScheduler scheduler = new BotRunScheduler(
                    config(),
                    telemetry
            );

            scheduler.reconcileRunningBots(Map.of(
                    7L,
                    new RunningBotScheduleState(true, true, false)
            ));

            assertEquals(0, scheduler.queuedCount());

            scheduler.reconcileRunningBots(Map.of(
                    7L,
                    new RunningBotScheduleState(true, true, true)
            ));

            assertEquals(1, scheduler.queuedCount());
            ScheduledBotTask recovery = scheduler.takeNext();
            assertEquals(ScheduledJobType.CAPTCHA_RECOVERY, recovery.jobType());

            scheduler.completeCaptchaRecovery(7L, false);
            assertEquals(0, scheduler.queuedCount());

            scheduler.reconcileRunningBots(Map.of(
                    7L,
                    new RunningBotScheduleState(true, true, false)
            ));
            assertEquals(0, scheduler.queuedCount());

            scheduler.reconcileRunningBots(Map.of(
                    7L,
                    new RunningBotScheduleState(true, false, false)
            ));

            ScheduledBotTask resumed = scheduler.takeNext();
            assertNotEquals(ScheduledJobType.CAPTCHA_RECOVERY, resumed.jobType());
        }
    }

    @Test
    public void detectedCaptchaRemovesNormalAutomaticSchedule()
            throws Exception {
        try (SilentTelemetryReporter telemetry =
                     new SilentTelemetryReporter()) {
            BotRunScheduler scheduler = new BotRunScheduler(
                    config(),
                    telemetry
            );

            scheduler.reconcileRunningBots(Map.of(
                    11L,
                    new RunningBotScheduleState(true, false, false)
            ));

            ScheduledBotTask normalTask = scheduler.takeNext();
            assertNotEquals(
                    ScheduledJobType.CAPTCHA_RECOVERY,
                    normalTask.jobType()
            );

            scheduler.pauseForCaptcha(11L);

            assertEquals(0, scheduler.queuedCount());
            assertEquals(0, scheduler.workingCount());
        }
    }

    private WorkerRuntimeConfig config() {
        return new WorkerRuntimeConfig(
                1,
                5,
                120,
                900,
                60,
                60,
                600,
                30,
                true
        );
    }

    private static final class SilentTelemetryReporter
            extends RuntimeTelemetryReporter {

        @Override
        public void queued(Long botId, long nextRunAtEpochMs) {
            // No backend is needed for this scheduler unit test.
        }

        @Override
        public void idle(Long botId) {
            // No backend is needed for this scheduler unit test.
        }
    }
}

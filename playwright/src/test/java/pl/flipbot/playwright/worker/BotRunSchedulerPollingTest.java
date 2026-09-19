package pl.flipbot.playwright.worker;

import org.junit.Test;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;

import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void catalogConcurrencyLimitDefersExtraCatalogWithoutBlockingNegotiation()
            throws Exception {
        NoOpTelemetryReporter telemetry = new NoOpTelemetryReporter();
        BotRunScheduler scheduler = new BotRunScheduler(
                config(),
                telemetry,
                new CatalogConcurrencyConfig(1, 250L)
        );

        try {
            scheduler.reconcileRunningBots(
                    Map.of(
                            1L, false,
                            2L, false
                    )
            );

            ScheduledBotTask firstCatalog = scheduler.pollNext(100L);

            assertNotNull(firstCatalog);
            assertEquals(Long.valueOf(1L), firstCatalog.botId());
            assertEquals(
                    ScheduledJobType.CATALOG_SCAN,
                    firstCatalog.jobType()
            );
            assertEquals(1, scheduler.workingCatalogCount());

            /*
             * Bot 2's ready catalog task is consumed and rescheduled because
             * the single catalog slot is already occupied. No worker remains
             * blocked waiting for capacity.
             */
            assertNull(scheduler.pollNext(30L));
            assertEquals(1, scheduler.workingCatalogCount());

            /*
             * A newly-active negotiation for bot 2 must pre-empt the deferred
             * catalog even while bot 1 still owns the catalog capacity.
             */
            scheduler.reconcileRunningBots(
                    Map.of(
                            1L, false,
                            2L, true
                    )
            );

            ScheduledBotTask negotiation = scheduler.pollNext(100L);

            assertNotNull(negotiation);
            assertEquals(Long.valueOf(2L), negotiation.botId());
            assertEquals(
                    ScheduledJobType.NEGOTIATION_CHECK,
                    negotiation.jobType()
            );
            assertEquals(1, scheduler.workingCatalogCount());
        } finally {
            scheduler.shutdown();
            telemetry.close();
        }
    }

    @Test
    public void pausedPreviewBotCannotBeClaimedAndResumesWithoutLosingDueWork()
            throws Exception {
        NoOpTelemetryReporter telemetry = new NoOpTelemetryReporter();
        BotRunScheduler scheduler = new BotRunScheduler(config(), telemetry);

        try {
            scheduler.setPausedBotIds(Set.of(7L));
            scheduler.reconcileRunningBots(Map.of(7L, false));

            assertTrue(scheduler.isPaused(7L));
            assertNull(scheduler.pollNext(30L));

            scheduler.setPausedBotIds(Set.of());

            ScheduledBotTask resumed = scheduler.pollNext(100L);

            assertNotNull(resumed);
            assertEquals(Long.valueOf(7L), resumed.botId());
            assertEquals(
                    ScheduledJobType.CATALOG_SCAN,
                    resumed.jobType()
            );
        } finally {
            scheduler.shutdown();
            telemetry.close();
        }
    }

    @Test
    public void previewPauseLetsAlreadyWorkingJobFinishButQueuesNothingUntilResume()
            throws Exception {
        NoOpTelemetryReporter telemetry = new NoOpTelemetryReporter();
        BotRunScheduler scheduler = new BotRunScheduler(config(), telemetry);

        try {
            scheduler.reconcileRunningBots(Map.of(8L, true));

            ScheduledBotTask running = scheduler.pollNext(100L);
            assertNotNull(running);
            assertTrue(scheduler.isWorking(8L));

            scheduler.setPausedBotIds(Set.of(8L));

            assertTrue(scheduler.isPaused(8L));
            assertTrue(scheduler.isWorking(8L));

            scheduler.completeRun(
                    running.botId(),
                    running.jobType(),
                    0L,
                    false,
                    true
            );

            assertFalse(scheduler.isWorking(8L));
            assertNull(scheduler.pollNext(30L));

            scheduler.setPausedBotIds(Set.of());

            ScheduledBotTask resumed = scheduler.pollNext(100L);
            assertNotNull(resumed);
            assertEquals(Long.valueOf(8L), resumed.botId());
        } finally {
            scheduler.shutdown();
            telemetry.close();
        }
    }

    @Test
    public void livePreviewOwnerKeepsRunningNormalDueJobsWhileGenericWorkersStayOut()
            throws Exception {
        NoOpTelemetryReporter telemetry = new NoOpTelemetryReporter();
        BotRunScheduler scheduler = new BotRunScheduler(config(), telemetry);

        try {
            scheduler.setPausedBotIds(Set.of(9L));
            scheduler.reconcileRunningBots(Map.of(9L, true));

            assertTrue(scheduler.isPaused(9L));
            assertNull(scheduler.pollNext(30L));

            ScheduledBotTask previewTask =
                    scheduler.pollPreviewNext(9L, 100L);

            assertNotNull(previewTask);
            assertEquals(Long.valueOf(9L), previewTask.botId());
            assertEquals(
                    ScheduledJobType.NEGOTIATION_CHECK,
                    previewTask.jobType()
            );
            assertTrue(scheduler.isWorking(9L));

            scheduler.completeRun(
                    previewTask.botId(),
                    previewTask.jobType(),
                    0L,
                    false,
                    true
            );

            assertFalse(scheduler.isWorking(9L));
            assertNull(scheduler.pollNext(30L));

            ScheduledBotTask secondPreviewTask =
                    scheduler.pollPreviewNext(9L, 100L);

            assertNotNull(secondPreviewTask);
            assertEquals(Long.valueOf(9L), secondPreviewTask.botId());
        } finally {
            scheduler.shutdown();
            telemetry.close();
        }
    }

    @Test
    public void catalogConcurrencyAllowsConfiguredNumberOfCatalogs()
            throws Exception {
        NoOpTelemetryReporter telemetry = new NoOpTelemetryReporter();
        BotRunScheduler scheduler = new BotRunScheduler(
                config(),
                telemetry,
                new CatalogConcurrencyConfig(2, 50L)
        );

        try {
            scheduler.reconcileRunningBots(
                    Map.of(
                            1L, false,
                            2L, false
                    )
            );

            ScheduledBotTask first = scheduler.pollNext(100L);
            ScheduledBotTask second = scheduler.pollNext(100L);

            assertNotNull(first);
            assertNotNull(second);
            assertEquals(ScheduledJobType.CATALOG_SCAN, first.jobType());
            assertEquals(ScheduledJobType.CATALOG_SCAN, second.jobType());
            assertEquals(2, scheduler.workingCatalogCount());
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

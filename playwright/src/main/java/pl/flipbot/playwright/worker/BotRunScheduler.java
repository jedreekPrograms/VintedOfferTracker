package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BotRunScheduler {

    private enum RunState {
        QUEUED,
        WORKING
    }

    private static final long NEVER = Long.MAX_VALUE;

    private final DelayQueue<ScheduledBotTask> queue =
            new DelayQueue<>();

    private final Map<Long, BotSchedule> schedules =
            new HashMap<>();

    private final WorkerRuntimeConfig config;
    private final RuntimeTelemetryReporter telemetryReporter;

    public BotRunScheduler(
            WorkerRuntimeConfig config,
            RuntimeTelemetryReporter telemetryReporter
    ) {
        this.config = config;
        this.telemetryReporter = telemetryReporter;
    }

    public synchronized void reconcileRunningBots(
            Map<Long, Boolean> runningBots
    ) {

        Map<Long, Boolean> normalizedRunningBots =
                new HashMap<>();

        runningBots.forEach(
                (botId, hasActiveNegotiations) -> {
                    if (botId != null && botId > 0) {
                        normalizedRunningBots.put(
                                botId,
                                Boolean.TRUE.equals(hasActiveNegotiations)
                        );
                    }
                }
        );

        Set<Long> botsToDisable =
                new HashSet<>(schedules.keySet());

        botsToDisable.removeAll(
                normalizedRunningBots.keySet()
        );

        for (Long botId : botsToDisable) {
            disableBot(botId);
        }

        long now = System.currentTimeMillis();

        normalizedRunningBots.forEach(
                (botId, hasActiveNegotiations) ->
                        enableOrRefreshBot(
                                botId,
                                hasActiveNegotiations,
                                now
                        )
        );
    }

    public ScheduledBotTask takeNext()
            throws InterruptedException {

        while (true) {
            ScheduledBotTask task = queue.take();

            synchronized (this) {
                BotSchedule schedule =
                        schedules.get(task.botId());

                if (schedule == null || !schedule.enabled) {
                    continue;
                }

                if (schedule.state != RunState.QUEUED) {
                    continue;
                }

                if (schedule.queuedJobType != task.jobType()) {
                    continue;
                }

                if (schedule.queuedRunAtNanos != task.runAtNanos()) {
                    continue;
                }

                schedule.state = RunState.WORKING;
                schedule.queuedJobType = null;
                schedule.queuedRunAtNanos = 0L;

                return task;
            }
        }
    }

    public synchronized void completeRun(
            Long botId,
            ScheduledJobType jobType,
            long nextDelayMillis,
            boolean delayAllJobs,
            boolean reportQueued
    ) {

        BotSchedule schedule = schedules.get(botId);

        if (schedule == null) {
            return;
        }

        if (!schedule.enabled) {
            schedules.remove(botId);
            telemetryReporter.idle(botId);

            log.info(
                    "[SCHEDULER] Bot {} finished {} after being stopped. No next job scheduled.",
                    botId,
                    jobType
            );
            return;
        }

        long now = System.currentTimeMillis();
        long safeDelayMillis = Math.max(0L, nextDelayMillis);
        long readyAt = safeAdd(now, safeDelayMillis);

        if (delayAllJobs) {
            schedule.nextCatalogAtEpochMs =
                    Math.max(
                            schedule.nextCatalogAtEpochMs,
                            readyAt
                    );

            if (schedule.hasActiveNegotiations) {
                schedule.nextNegotiationAtEpochMs =
                        Math.max(
                                schedule.nextNegotiationAtEpochMs,
                                readyAt
                        );
            } else {
                schedule.nextNegotiationAtEpochMs = NEVER;
            }

        } else if (jobType == ScheduledJobType.CATALOG_SCAN) {
            schedule.nextCatalogAtEpochMs = readyAt;

        } else if (schedule.hasActiveNegotiations) {
            schedule.nextNegotiationAtEpochMs = readyAt;

        } else {
            schedule.nextNegotiationAtEpochMs = NEVER;
        }

        schedule.reportQueuedStatus = reportQueued;
        schedule.state = null;

        enqueueEarliestJob(
                botId,
                schedule,
                now
        );
    }

    public synchronized void shutdown() {
        schedules.clear();
        queue.clear();
    }

    public synchronized int queuedCount() {
        return queue.size();
    }

    public synchronized int workingCount() {
        return (int) schedules.values()
                .stream()
                .filter(schedule -> schedule.state == RunState.WORKING)
                .count();
    }

    public synchronized int enabledBotCount() {
        return (int) schedules.values()
                .stream()
                .filter(schedule -> schedule.enabled)
                .count();
    }

    private void disableBot(Long botId) {
        BotSchedule schedule = schedules.get(botId);

        if (schedule == null) {
            return;
        }

        schedule.enabled = false;

        if (schedule.state == RunState.QUEUED) {
            removeQueuedTask(botId);
            schedules.remove(botId);
            telemetryReporter.idle(botId);

            log.info(
                    "[SCHEDULER] Removed queued work for stopped bot {}.",
                    botId
            );

        } else if (schedule.state == RunState.WORKING) {
            log.info(
                    "[SCHEDULER] Bot {} was stopped while WORKING. "
                            + "The current job may finish, but no next job will be scheduled.",
                    botId
            );
        }
    }

    private void enableOrRefreshBot(
            Long botId,
            boolean hasActiveNegotiations,
            long now
    ) {

        BotSchedule schedule = schedules.get(botId);

        if (schedule == null) {
            BotSchedule newSchedule = new BotSchedule();
            newSchedule.enabled = true;
            newSchedule.hasActiveNegotiations = hasActiveNegotiations;
            newSchedule.nextCatalogAtEpochMs = now;
            newSchedule.nextNegotiationAtEpochMs =
                    hasActiveNegotiations
                            ? now
                            : NEVER;
            newSchedule.reportQueuedStatus = true;

            schedules.put(botId, newSchedule);

            enqueueEarliestJob(
                    botId,
                    newSchedule,
                    now
            );

            log.info(
                    "[SCHEDULER] Enabled bot {}. Active negotiations={}.",
                    botId,
                    hasActiveNegotiations
            );
            return;
        }

        boolean negotiationsChanged =
                schedule.hasActiveNegotiations
                        != hasActiveNegotiations;

        schedule.enabled = true;
        schedule.hasActiveNegotiations = hasActiveNegotiations;

        if (negotiationsChanged) {
            if (hasActiveNegotiations) {
                schedule.nextNegotiationAtEpochMs = now;
            } else {
                schedule.nextNegotiationAtEpochMs = NEVER;
            }

            if (schedule.state == RunState.QUEUED) {
                removeQueuedTask(botId);
                schedule.state = null;

                enqueueEarliestJob(
                        botId,
                        schedule,
                        now
                );
            }

            log.info(
                    "[SCHEDULER] Bot {} active-negotiation flag changed to {}.",
                    botId,
                    hasActiveNegotiations
            );
        }
    }

    private void enqueueEarliestJob(
            Long botId,
            BotSchedule schedule,
            long now
    ) {

        if (!schedule.enabled) {
            return;
        }

        ScheduledJobType jobType;
        long runAtEpochMs;

        if (schedule.hasActiveNegotiations
                && schedule.nextNegotiationAtEpochMs
                <= schedule.nextCatalogAtEpochMs) {

            jobType = ScheduledJobType.NEGOTIATION_CHECK;
            runAtEpochMs = schedule.nextNegotiationAtEpochMs;

        } else {
            jobType = ScheduledJobType.CATALOG_SCAN;
            runAtEpochMs = schedule.nextCatalogAtEpochMs;
        }

        long delayMillis =
                Math.max(
                        0L,
                        runAtEpochMs - now
                );

        ScheduledBotTask task =
                ScheduledBotTask.afterDelay(
                        botId,
                        jobType,
                        delayMillis
                );

        queue.offer(task);

        schedule.state = RunState.QUEUED;
        schedule.queuedJobType = jobType;
        schedule.queuedRunAtNanos = task.runAtNanos();

        if (schedule.reportQueuedStatus) {
            telemetryReporter.queued(
                    botId,
                    runAtEpochMs
            );
        }

        log.info(
                "[SCHEDULER] Bot {} queued {} in {} ms. Queue size: {}.",
                botId,
                jobType,
                delayMillis,
                queue.size()
        );
    }

    private void removeQueuedTask(Long botId) {
        queue.removeIf(
                task -> botId.equals(task.botId())
        );
    }

    private long safeAdd(
            long base,
            long increment
    ) {
        if (increment > Long.MAX_VALUE - base) {
            return Long.MAX_VALUE;
        }

        return base + increment;
    }

    private static final class BotSchedule {
        private boolean enabled;
        private boolean hasActiveNegotiations;
        private RunState state;
        private long nextCatalogAtEpochMs;
        private long nextNegotiationAtEpochMs = NEVER;
        private ScheduledJobType queuedJobType;
        private long queuedRunAtNanos;
        private boolean reportQueuedStatus;
    }
}

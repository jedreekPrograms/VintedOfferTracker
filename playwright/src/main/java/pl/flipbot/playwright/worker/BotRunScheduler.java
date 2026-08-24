package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.probe.PriceProbeRuntimeConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.DelayQueue;

@Slf4j
public class BotRunScheduler {

    private enum RunState {
        QUEUED,
        WORKING
    }

    private static final long NEVER = Long.MAX_VALUE;

    private static final PriceProbeRuntimeConfig PRICE_PROBE_CONFIG =
            PriceProbeRuntimeConfig.fromEnvironment();

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
            return;
        }

        long now = System.currentTimeMillis();
        long safeDelayMillis = Math.max(0L, nextDelayMillis);
        long readyAt = safeAdd(now, safeDelayMillis);

        if (delayAllJobs) {
            schedule.nextCatalogAtEpochMs = Math.max(
                    schedule.nextCatalogAtEpochMs,
                    readyAt
            );

            schedule.nextNegotiationAtEpochMs =
                    schedule.hasActiveNegotiations
                            ? Math.max(schedule.nextNegotiationAtEpochMs, readyAt)
                            : NEVER;

            schedule.nextPriceProbeAtEpochMs =
                    PRICE_PROBE_CONFIG.enabled()
                            ? Math.max(schedule.nextPriceProbeAtEpochMs, readyAt)
                            : NEVER;

        } else {
            switch (jobType) {
                case CATALOG_SCAN ->
                        schedule.nextCatalogAtEpochMs = readyAt;
                case NEGOTIATION_CHECK ->
                        schedule.nextNegotiationAtEpochMs =
                                schedule.hasActiveNegotiations
                                        ? readyAt
                                        : NEVER;
                case PRICE_PROBE ->
                        schedule.nextPriceProbeAtEpochMs =
                                PRICE_PROBE_CONFIG.enabled()
                                        ? readyAt
                                        : NEVER;
            }
        }

        schedule.reportQueuedStatus = reportQueued;
        schedule.state = null;

        enqueueEarliestJob(botId, schedule, now);
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
                    hasActiveNegotiations ? now : NEVER;
            newSchedule.nextPriceProbeAtEpochMs =
                    PRICE_PROBE_CONFIG.enabled() ? now : NEVER;
            newSchedule.reportQueuedStatus = true;

            schedules.put(botId, newSchedule);
            enqueueEarliestJob(botId, newSchedule, now);
            return;
        }

        boolean negotiationsChanged =
                schedule.hasActiveNegotiations != hasActiveNegotiations;

        schedule.enabled = true;
        schedule.hasActiveNegotiations = hasActiveNegotiations;

        if (!PRICE_PROBE_CONFIG.enabled()) {
            schedule.nextPriceProbeAtEpochMs = NEVER;
        }

        if (negotiationsChanged) {
            schedule.nextNegotiationAtEpochMs =
                    hasActiveNegotiations ? now : NEVER;

            if (schedule.state == RunState.QUEUED) {
                removeQueuedTask(botId);
                schedule.state = null;
                enqueueEarliestJob(botId, schedule, now);
            }
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

        ScheduledJobType jobType = ScheduledJobType.CATALOG_SCAN;
        long runAtEpochMs = schedule.nextCatalogAtEpochMs;

        if (schedule.hasActiveNegotiations
                && schedule.nextNegotiationAtEpochMs <= runAtEpochMs) {
            jobType = ScheduledJobType.NEGOTIATION_CHECK;
            runAtEpochMs = schedule.nextNegotiationAtEpochMs;
        }

        if (PRICE_PROBE_CONFIG.enabled()
                && schedule.nextPriceProbeAtEpochMs < runAtEpochMs) {
            jobType = ScheduledJobType.PRICE_PROBE;
            runAtEpochMs = schedule.nextPriceProbeAtEpochMs;
        }

        long delayMillis = Math.max(0L, runAtEpochMs - now);

        ScheduledBotTask task = ScheduledBotTask.afterDelay(
                botId,
                jobType,
                delayMillis
        );

        queue.offer(task);

        schedule.state = RunState.QUEUED;
        schedule.queuedJobType = jobType;
        schedule.queuedRunAtNanos = task.runAtNanos();

        if (schedule.reportQueuedStatus) {
            telemetryReporter.queued(botId, runAtEpochMs);
        }
    }

    private void removeQueuedTask(Long botId) {
        queue.removeIf(task -> botId.equals(task.botId()));
    }

    private long safeAdd(long base, long increment) {
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
        private long nextPriceProbeAtEpochMs = NEVER;
        private ScheduledJobType queuedJobType;
        private long queuedRunAtNanos;
        private boolean reportQueuedStatus;
    }
}

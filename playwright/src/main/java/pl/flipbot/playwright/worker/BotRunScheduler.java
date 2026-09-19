package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.probe.PriceProbeRuntimeConfig;

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

    private static final PriceProbeRuntimeConfig PRICE_PROBE_CONFIG =
            PriceProbeRuntimeConfig.fromEnvironment();

    private final DelayQueue<ScheduledBotTask> queue =
            new DelayQueue<>();

    private final Map<Long, BotSchedule> schedules =
            new HashMap<>();

    /*
     * Session preview owns the bot's Vinted session while it is visible.
     * Paused bots keep their due timestamps, but no scheduled job may be
     * queued or claimed until the preview runtime has closed on its owner
     * thread. This prevents two browser contexts from using the same account
     * session at the same time.
     */
    private final Set<Long> pausedBotIds =
            new HashSet<>();

    private final WorkerRuntimeConfig config;
    private final RuntimeTelemetryReporter telemetryReporter;
    private final CatalogConcurrencyConfig catalogConcurrencyConfig;

    public BotRunScheduler(
            WorkerRuntimeConfig config,
            RuntimeTelemetryReporter telemetryReporter
    ) {
        this(
                config,
                telemetryReporter,
                CatalogConcurrencyConfig.fromEnvironment()
        );
    }

    BotRunScheduler(
            WorkerRuntimeConfig config,
            RuntimeTelemetryReporter telemetryReporter,
            CatalogConcurrencyConfig catalogConcurrencyConfig
    ) {
        this.config = config;
        this.telemetryReporter = telemetryReporter;
        this.catalogConcurrencyConfig = catalogConcurrencyConfig;

        log.info(
                "[SCHEDULER MEMORY] Max concurrent catalog scans={}. Deferred catalog retry={} ms.",
                catalogConcurrencyConfig.maxConcurrentCatalogScans(),
                catalogConcurrencyConfig.retryDelayMillis()
        );
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
            ScheduledBotTask claimed = claimIfCurrent(queue.take());
            if (claimed != null) {
                return claimed;
            }
        }
    }

    /**
     * Waits for the next ready, still-current task for at most the supplied
     * timeout. A null result means the worker may perform idle maintenance,
     * such as releasing an otherwise unused Chromium runtime.
     */
    public ScheduledBotTask pollNext(long timeoutMillis)
            throws InterruptedException {

        if (timeoutMillis < 0L) {
            throw new IllegalArgumentException(
                    "Scheduler poll timeout cannot be negative."
            );
        }

        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        while (true) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                return null;
            }

            ScheduledBotTask task = queue.poll(
                    remainingNanos,
                    TimeUnit.NANOSECONDS
            );

            if (task == null) {
                return null;
            }

            ScheduledBotTask claimed = claimIfCurrent(task);
            if (claimed != null) {
                return claimed;
            }
        }
    }

    /**
     * Claims due work for a bot whose session is exclusively owned by the
     * visible live-preview worker. Generic worker slots cannot claim paused
     * bots, but the dedicated preview owner must keep the bot operating on its
     * normal schedule while the user watches it.
     */
    public ScheduledBotTask pollPreviewNext(
            Long botId,
            long timeoutMillis
    ) throws InterruptedException {
        if (botId == null || botId <= 0L) {
            throw new IllegalArgumentException("Preview bot ID must be positive.");
        }
        if (timeoutMillis < 0L) {
            throw new IllegalArgumentException(
                    "Preview scheduler poll timeout cannot be negative."
            );
        }

        long deadlineNanos = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        while (true) {
            ScheduledBotTask claimed;

            synchronized (this) {
                claimed = claimPreviewIfDue(botId);
            }

            if (claimed != null) {
                return claimed;
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                return null;
            }

            long sleepMillis = Math.min(
                    250L,
                    Math.max(
                            1L,
                            TimeUnit.NANOSECONDS.toMillis(remainingNanos)
                    )
            );
            Thread.sleep(sleepMillis);
        }
    }

    private ScheduledBotTask claimPreviewIfDue(Long botId) {
        BotSchedule schedule = schedules.get(botId);

        if (schedule == null
                || !schedule.enabled
                || !pausedBotIds.contains(botId)
                || schedule.state == RunState.WORKING) {
            return null;
        }

        /*
         * setPausedBotIds normally removes queued work before preview starts.
         * Clear any stale queued marker defensively so the preview owner is the
         * only claimant for this bot.
         */
        if (schedule.state == RunState.QUEUED) {
            removeQueuedTask(botId);
            schedule.state = null;
            schedule.queuedJobType = null;
            schedule.queuedRunAtNanos = 0L;
        }

        NextJob next = nextJob(schedule);
        long now = System.currentTimeMillis();

        if (next.runAtEpochMs() > now) {
            return null;
        }

        if (next.jobType() == ScheduledJobType.CATALOG_SCAN
                && workingCatalogCountUnsafe()
                >= catalogConcurrencyConfig.maxConcurrentCatalogScans()) {
            schedule.nextCatalogAtEpochMs = Math.max(
                    schedule.nextCatalogAtEpochMs,
                    safeAdd(now, catalogConcurrencyConfig.retryDelayMillis())
            );
            return null;
        }

        schedule.state = RunState.WORKING;
        schedule.workingJobType = next.jobType();
        schedule.queuedJobType = null;
        schedule.queuedRunAtNanos = 0L;

        return ScheduledBotTask.afterDelay(
                botId,
                next.jobType(),
                0L
        );
    }

    private synchronized ScheduledBotTask claimIfCurrent(
            ScheduledBotTask task
    ) {
        BotSchedule schedule = schedules.get(task.botId());

        if (schedule == null || !schedule.enabled) {
            return null;
        }

        if (pausedBotIds.contains(task.botId())) {
            if (schedule.state == RunState.QUEUED
                    && schedule.queuedJobType == task.jobType()
                    && schedule.queuedRunAtNanos == task.runAtNanos()) {
                schedule.state = null;
                schedule.queuedJobType = null;
                schedule.queuedRunAtNanos = 0L;
            }
            return null;
        }

        if (schedule.state != RunState.QUEUED) {
            return null;
        }

        if (schedule.queuedJobType != task.jobType()) {
            return null;
        }

        if (schedule.queuedRunAtNanos != task.runAtNanos()) {
            return null;
        }

        if (task.jobType() == ScheduledJobType.CATALOG_SCAN
                && workingCatalogCountUnsafe()
                >= catalogConcurrencyConfig.maxConcurrentCatalogScans()) {
            deferCatalogForCapacity(
                    task.botId(),
                    schedule
            );
            return null;
        }

        schedule.state = RunState.WORKING;
        schedule.workingJobType = task.jobType();
        schedule.queuedJobType = null;
        schedule.queuedRunAtNanos = 0L;

        return task;
    }

    private void deferCatalogForCapacity(
            Long botId,
            BotSchedule schedule
    ) {
        long now = System.currentTimeMillis();
        long retryAt = safeAdd(
                now,
                catalogConcurrencyConfig.retryDelayMillis()
        );

        schedule.nextCatalogAtEpochMs = Math.max(
                schedule.nextCatalogAtEpochMs,
                retryAt
        );
        schedule.state = null;
        schedule.queuedJobType = null;
        schedule.queuedRunAtNanos = 0L;
        schedule.reportQueuedStatus = false;

        enqueueEarliestJob(botId, schedule, now);

        log.debug(
                "[SCHEDULER MEMORY] Deferred CATALOG_SCAN for bot {} because {}/{} catalog scans are already working. Retry in {} ms; negotiation/price-probe work remains eligible.",
                botId,
                workingCatalogCountUnsafe(),
                catalogConcurrencyConfig.maxConcurrentCatalogScans(),
                catalogConcurrencyConfig.retryDelayMillis()
        );
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
            BotProcessStateCleaner.clear(botId);
            telemetryReporter.idle(botId);
            return;
        }

        schedule.workingJobType = null;

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

        if (!pausedBotIds.contains(botId)) {
            enqueueEarliestJob(botId, schedule, now);
        }
    }

    public synchronized void setPausedBotIds(Set<Long> requestedPausedBotIds) {
        Set<Long> normalized = new HashSet<>();

        if (requestedPausedBotIds != null) {
            requestedPausedBotIds.stream()
                    .filter(botId -> botId != null && botId > 0L)
                    .forEach(normalized::add);
        }

        Set<Long> newlyPaused = new HashSet<>(normalized);
        newlyPaused.removeAll(pausedBotIds);

        Set<Long> resumed = new HashSet<>(pausedBotIds);
        resumed.removeAll(normalized);

        long now = System.currentTimeMillis();

        for (Long botId : newlyPaused) {
            BotSchedule schedule = schedules.get(botId);

            if (schedule == null) {
                continue;
            }

            if (schedule.state == RunState.QUEUED) {
                removeQueuedTask(botId);
                schedule.state = null;
                schedule.queuedJobType = null;
                schedule.queuedRunAtNanos = 0L;
            }

            log.info(
                    "[SESSION PREVIEW] Scheduler paused bot {}. Existing WORKING job, if any, may finish; no new job will be claimed.",
                    botId
            );
        }

        pausedBotIds.clear();
        pausedBotIds.addAll(normalized);

        for (Long botId : resumed) {
            BotSchedule schedule = schedules.get(botId);

            if (schedule == null
                    || !schedule.enabled
                    || schedule.state != null) {
                continue;
            }

            schedule.reportQueuedStatus = true;
            enqueueEarliestJob(botId, schedule, now);

            log.info(
                    "[SESSION PREVIEW] Scheduler resumed bot {} after preview ownership ended.",
                    botId
            );
        }
    }

    public synchronized boolean isWorking(Long botId) {
        BotSchedule schedule = schedules.get(botId);

        return schedule != null
                && schedule.state == RunState.WORKING;
    }

    public synchronized boolean isPaused(Long botId) {
        return botId != null && pausedBotIds.contains(botId);
    }

    public synchronized void shutdown() {
        schedules.clear();
        queue.clear();
        pausedBotIds.clear();
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

    public synchronized int workingCatalogCount() {
        return workingCatalogCountUnsafe();
    }

    public synchronized int enabledBotCount() {
        return (int) schedules.values()
                .stream()
                .filter(schedule -> schedule.enabled)
                .count();
    }

    private int workingCatalogCountUnsafe() {
        return (int) schedules.values()
                .stream()
                .filter(schedule -> schedule.state == RunState.WORKING)
                .filter(
                        schedule -> schedule.workingJobType
                                == ScheduledJobType.CATALOG_SCAN
                )
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
            BotProcessStateCleaner.clear(botId);
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

        if (!schedule.enabled
                || pausedBotIds.contains(botId)) {
            return;
        }

        NextJob next = nextJob(schedule);
        ScheduledJobType jobType = next.jobType();
        long runAtEpochMs = next.runAtEpochMs();

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

    private NextJob nextJob(BotSchedule schedule) {
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

        return new NextJob(jobType, runAtEpochMs);
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

    private record NextJob(
            ScheduledJobType jobType,
            long runAtEpochMs
    ) {
    }

    private static final class BotSchedule {
        private boolean enabled;
        private boolean hasActiveNegotiations;
        private RunState state;
        private ScheduledJobType workingJobType;
        private long nextCatalogAtEpochMs;
        private long nextNegotiationAtEpochMs = NEVER;
        private long nextPriceProbeAtEpochMs = NEVER;
        private ScheduledJobType queuedJobType;
        private long queuedRunAtNanos;
        private boolean reportQueuedStatus;
    }
}

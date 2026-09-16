package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.probe.PriceProbeRuntimeConfig;

import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BotRunScheduler {

    private static final long NEVER = Long.MAX_VALUE;

    private static final PriceProbeRuntimeConfig PRICE_PROBE_CONFIG =
            PriceProbeRuntimeConfig.fromEnvironment();

    private final Map<Long, BotSchedule> schedules = new HashMap<>();
    private final WorkerRuntimeConfig config;
    private final RuntimeTelemetryReporter telemetryReporter;
    private final CatalogConcurrencyConfig catalogConcurrencyConfig;
    private final Clock clock;

    private boolean shuttingDown;

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
        this(config, telemetryReporter, catalogConcurrencyConfig, Clock.systemUTC());
    }

    BotRunScheduler(
            WorkerRuntimeConfig config,
            RuntimeTelemetryReporter telemetryReporter,
            CatalogConcurrencyConfig catalogConcurrencyConfig,
            Clock clock
    ) {
        this.config = config;
        this.telemetryReporter = telemetryReporter;
        this.catalogConcurrencyConfig = catalogConcurrencyConfig;
        this.clock = clock;

        log.info(
                "[SCHEDULER CAPACITY] Max concurrent catalog scans={}. Catalog work waits for a released slot instead of retry-polling every second.",
                catalogConcurrencyConfig.maxConcurrentCatalogScans()
        );
    }

    public synchronized void reconcileRunningBots(
            Map<Long, Boolean> runningBots
    ) {
        if (shuttingDown) {
            return;
        }

        Map<Long, Boolean> normalizedRunningBots = new HashMap<>();

        runningBots.forEach((botId, hasActiveNegotiations) -> {
            if (botId != null && botId > 0) {
                normalizedRunningBots.put(
                        botId,
                        Boolean.TRUE.equals(hasActiveNegotiations)
                );
            }
        });

        Set<Long> botsToDisable = new HashSet<>(schedules.keySet());
        botsToDisable.removeAll(normalizedRunningBots.keySet());

        for (Long botId : botsToDisable) {
            disableBot(botId);
        }

        long now = clock.millis();

        normalizedRunningBots.forEach(
                (botId, hasActiveNegotiations) -> enableOrRefreshBot(
                        botId,
                        hasActiveNegotiations,
                        now
                )
        );

        notifyAll();
    }

    public synchronized ScheduledBotTask takeNext()
            throws InterruptedException {
        while (true) {
            if (shuttingDown) {
                throw new InterruptedException(
                        "Scheduler is shutting down."
                );
            }

            long now = clock.millis();
            Candidate candidate = bestClaimableCandidateUnsafe(now);

            if (candidate != null) {
                return claimUnsafe(candidate);
            }

            long waitMillis = millisUntilNextPotentialJobUnsafe(now);

            if (waitMillis == NEVER) {
                wait();
            } else {
                wait(Math.max(1L, waitMillis));
            }
        }
    }

    /**
     * Waits for a ready job that is both due and currently eligible for its
     * resource capacity. A due catalog does not occupy a worker while all
     * catalog slots are busy; releasing catalog capacity wakes waiters.
     */
    public synchronized ScheduledBotTask pollNext(long timeoutMillis)
            throws InterruptedException {
        if (timeoutMillis < 0L) {
            throw new IllegalArgumentException(
                    "Scheduler poll timeout cannot be negative."
            );
        }

        if (timeoutMillis == 0L || shuttingDown) {
            return claimReadyNowUnsafe();
        }

        long timeoutNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        long startedAtNanos = System.nanoTime();

        while (!shuttingDown) {
            ScheduledBotTask ready = claimReadyNowUnsafe();
            if (ready != null) {
                return ready;
            }

            long elapsedNanos = Math.max(
                    0L,
                    System.nanoTime() - startedAtNanos
            );
            long remainingNanos = timeoutNanos - elapsedNanos;

            if (remainingNanos <= 0L) {
                return null;
            }

            long remainingMillis = Math.max(
                    1L,
                    TimeUnit.NANOSECONDS.toMillis(remainingNanos)
            );
            long untilPotentialJob = millisUntilNextPotentialJobUnsafe(
                    clock.millis()
            );

            long waitMillis = untilPotentialJob == NEVER
                    ? remainingMillis
                    : Math.min(
                            remainingMillis,
                            Math.max(1L, untilPotentialJob)
                    );

            wait(waitMillis);
        }

        return null;
    }

    private ScheduledBotTask claimReadyNowUnsafe() {
        Candidate candidate = bestClaimableCandidateUnsafe(
                clock.millis()
        );

        return candidate == null ? null : claimUnsafe(candidate);
    }

    private ScheduledBotTask claimUnsafe(Candidate candidate) {
        BotSchedule schedule = schedules.get(candidate.botId());

        if (schedule == null
                || !schedule.enabled
                || schedule.workingJobType != null) {
            return null;
        }

        schedule.workingJobType = candidate.jobType();

        return ScheduledBotTask.afterDelay(
                candidate.botId(),
                candidate.jobType(),
                0L
        );
    }

    private Candidate bestClaimableCandidateUnsafe(long now) {
        Candidate best = null;
        boolean catalogCapacityAvailable =
                workingCatalogCountUnsafe()
                        < catalogConcurrencyConfig.maxConcurrentCatalogScans();

        for (Map.Entry<Long, BotSchedule> entry : schedules.entrySet()) {
            Long botId = entry.getKey();
            BotSchedule schedule = entry.getValue();

            if (!schedule.enabled || schedule.workingJobType != null) {
                continue;
            }

            Candidate candidate = dueCandidateForBotUnsafe(
                    botId,
                    schedule,
                    now,
                    catalogCapacityAvailable
            );

            if (candidate == null) {
                continue;
            }

            if (best == null || compareCandidates(candidate, best) < 0) {
                best = candidate;
            }
        }

        return best;
    }

    /**
     * Negotiation has first refusal for a bot when it is already due. This
     * prevents a bot's overdue catalog from hiding a seller response. Across
     * different bots, the oldest due timestamp wins, so catalogs cannot starve
     * behind a permanently busy negotiation population.
     */
    private Candidate dueCandidateForBotUnsafe(
            Long botId,
            BotSchedule schedule,
            long now,
            boolean catalogCapacityAvailable
    ) {
        if (schedule.hasActiveNegotiations
                && schedule.nextNegotiationAtEpochMs <= now) {
            return new Candidate(
                    botId,
                    ScheduledJobType.NEGOTIATION_CHECK,
                    schedule.nextNegotiationAtEpochMs
            );
        }

        if (catalogCapacityAvailable
                && schedule.nextCatalogAtEpochMs <= now) {
            return new Candidate(
                    botId,
                    ScheduledJobType.CATALOG_SCAN,
                    schedule.nextCatalogAtEpochMs
            );
        }

        if (PRICE_PROBE_CONFIG.enabled()
                && schedule.nextPriceProbeAtEpochMs <= now) {
            return new Candidate(
                    botId,
                    ScheduledJobType.PRICE_PROBE,
                    schedule.nextPriceProbeAtEpochMs
            );
        }

        return null;
    }

    private int compareCandidates(Candidate left, Candidate right) {
        int dueComparison = Long.compare(
                left.dueAtEpochMs(),
                right.dueAtEpochMs()
        );

        if (dueComparison != 0) {
            return dueComparison;
        }

        int priorityComparison = Integer.compare(
                jobPriority(left.jobType()),
                jobPriority(right.jobType())
        );

        if (priorityComparison != 0) {
            return priorityComparison;
        }

        return Long.compare(left.botId(), right.botId());
    }

    private int jobPriority(ScheduledJobType jobType) {
        return switch (jobType) {
            case NEGOTIATION_CHECK -> 0;
            case CATALOG_SCAN -> 1;
            case PRICE_PROBE -> 2;
        };
    }

    private long millisUntilNextPotentialJobUnsafe(long now) {
        long earliestEpochMs = NEVER;
        boolean catalogCapacityAvailable =
                workingCatalogCountUnsafe()
                        < catalogConcurrencyConfig.maxConcurrentCatalogScans();

        for (BotSchedule schedule : schedules.values()) {
            if (!schedule.enabled || schedule.workingJobType != null) {
                continue;
            }

            if (schedule.hasActiveNegotiations) {
                earliestEpochMs = Math.min(
                        earliestEpochMs,
                        schedule.nextNegotiationAtEpochMs
                );
            }

            if (catalogCapacityAvailable) {
                earliestEpochMs = Math.min(
                        earliestEpochMs,
                        schedule.nextCatalogAtEpochMs
                );
            }

            if (PRICE_PROBE_CONFIG.enabled()) {
                earliestEpochMs = Math.min(
                        earliestEpochMs,
                        schedule.nextPriceProbeAtEpochMs
                );
            }
        }

        if (earliestEpochMs == NEVER) {
            return NEVER;
        }

        return Math.max(0L, earliestEpochMs - now);
    }

    public synchronized void completeRun(
            Long botId,
            ScheduledJobType jobType,
            long nextDelayMillis,
            boolean delayAllJobs,
            boolean reportQueued
    ) {
        completeRunUnsafe(botId, jobType, nextDelayMillis, delayAllJobs, reportQueued, null);
    }

    /**
     * Compute the retry and publish its failure event before releasing the
     * scheduler monitor. A following RUN_STARTED cannot overtake this event,
     * and QUEUED must not immediately erase ERROR on the dashboard.
     */
    public synchronized void completeFailedRun(
            Long botId,
            ScheduledJobType jobType,
            long fallbackDelayMillis,
            long durationMillis,
            String errorMessage
    ) {
        completeRunUnsafe(
                botId, jobType, fallbackDelayMillis, false, false,
                new RunFailure(durationMillis, errorMessage)
        );
    }

    private void completeRunUnsafe(
            Long botId,
            ScheduledJobType jobType,
            long nextDelayMillis,
            boolean delayAllJobs,
            boolean reportQueued,
            RunFailure failure
    ) {
        BotSchedule schedule = schedules.get(botId);

        if (schedule == null) {
            notifyAll();
            return;
        }

        if (!schedule.enabled) {
            schedules.remove(botId);
            BotProcessStateCleaner.clear(botId);
            telemetryReporter.idle(botId);
            notifyAll();
            return;
        }

        if (schedule.workingJobType != jobType) {
            log.warn(
                    "[SCHEDULER] Bot {} completed {}, but scheduler recorded workingJobType={}. Releasing the bot conservatively.",
                    botId,
                    jobType,
                    schedule.workingJobType
            );
        }

        schedule.workingJobType = null;

        boolean adaptiveFailure = !delayAllJobs && !reportQueued;
        long safeDelayMillis = Math.max(0L, nextDelayMillis);

        if (adaptiveFailure) {
            int failureCount = incrementFailureCountUnsafe(
                    schedule,
                    jobType
            );

            safeDelayMillis = ScheduledJobFailureBackoffPolicy.delayMillis(
                    jobType,
                    failureCount,
                    safeDelayMillis
            );

            log.warn(
                    "[SCHEDULER BACKOFF] Bot {} {} consecutive failure #{}. Retry delayed by about {} minute(s). Other job types keep independent schedules and counters.",
                    botId,
                    jobType,
                    failureCount,
                    Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(safeDelayMillis))
            );
        } else if (reportQueued) {
            resetFailureCountUnsafe(schedule, jobType);
        }

        long now = clock.millis();
        long readyAt = safeAdd(now, safeDelayMillis);

        if (delayAllJobs) {
            schedule.nextCatalogAtEpochMs = Math.max(
                    schedule.nextCatalogAtEpochMs,
                    readyAt
            );

            schedule.nextNegotiationAtEpochMs =
                    schedule.hasActiveNegotiations
                            ? Math.max(
                                    schedule.nextNegotiationAtEpochMs,
                                    readyAt
                            )
                            : NEVER;

            schedule.nextPriceProbeAtEpochMs =
                    PRICE_PROBE_CONFIG.enabled()
                            ? Math.max(
                                    schedule.nextPriceProbeAtEpochMs,
                                    readyAt
                            )
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

        if (failure != null) {
            telemetryReporter.runFailed(
                    botId,
                    failure.durationMillis(),
                    earliestScheduledEpochMsUnsafe(schedule),
                    failure.errorMessage()
            );
        } else if (reportQueued || adaptiveFailure) {
            reportQueuedStateUnsafe(botId, schedule);
        }

        /*
         * This notification is the capacity hand-off. In particular, when a
         * CATALOG_SCAN finishes, workers waiting because all catalog slots were
         * occupied can immediately re-evaluate the oldest eligible work.
         */
        notifyAll();
    }

    public synchronized void shutdown() {
        shuttingDown = true;
        schedules.clear();
        notifyAll();
    }

    public synchronized int queuedCount() {
        return (int) schedules.values()
                .stream()
                .filter(schedule -> schedule.enabled)
                .filter(schedule -> schedule.workingJobType == null)
                .filter(this::hasAnyScheduledJobUnsafe)
                .count();
    }

    public synchronized int workingCount() {
        return (int) schedules.values()
                .stream()
                .filter(schedule -> schedule.workingJobType != null)
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
                .filter(
                        schedule -> schedule.workingJobType
                                == ScheduledJobType.CATALOG_SCAN
                )
                .count();
    }

    private boolean hasAnyScheduledJobUnsafe(BotSchedule schedule) {
        return schedule.nextCatalogAtEpochMs != NEVER
                || schedule.nextNegotiationAtEpochMs != NEVER
                || schedule.nextPriceProbeAtEpochMs != NEVER;
    }

    private void disableBot(Long botId) {
        BotSchedule schedule = schedules.get(botId);

        if (schedule == null) {
            return;
        }

        schedule.enabled = false;

        if (schedule.workingJobType == null) {
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

            schedules.put(botId, newSchedule);
            reportQueuedStateUnsafe(botId, newSchedule);
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

            if (schedule.workingJobType == null) {
                reportQueuedStateUnsafe(botId, schedule);
            }
        }
    }

    private void reportQueuedStateUnsafe(
            Long botId,
            BotSchedule schedule
    ) {
        long nextRunAt = earliestScheduledEpochMsUnsafe(schedule);

        if (nextRunAt != NEVER) {
            telemetryReporter.queued(botId, nextRunAt);
        }
    }

    private long earliestScheduledEpochMsUnsafe(BotSchedule schedule) {
        long earliest = schedule.nextCatalogAtEpochMs;

        if (schedule.hasActiveNegotiations) {
            earliest = Math.min(
                    earliest,
                    schedule.nextNegotiationAtEpochMs
            );
        }

        if (PRICE_PROBE_CONFIG.enabled()) {
            earliest = Math.min(
                    earliest,
                    schedule.nextPriceProbeAtEpochMs
            );
        }

        return earliest;
    }

    private int incrementFailureCountUnsafe(
            BotSchedule schedule,
            ScheduledJobType jobType
    ) {
        return switch (jobType) {
            case CATALOG_SCAN -> ++schedule.catalogFailureCount;
            case NEGOTIATION_CHECK -> ++schedule.negotiationFailureCount;
            case PRICE_PROBE -> ++schedule.priceProbeFailureCount;
        };
    }

    private void resetFailureCountUnsafe(
            BotSchedule schedule,
            ScheduledJobType jobType
    ) {
        switch (jobType) {
            case CATALOG_SCAN -> schedule.catalogFailureCount = 0;
            case NEGOTIATION_CHECK -> schedule.negotiationFailureCount = 0;
            case PRICE_PROBE -> schedule.priceProbeFailureCount = 0;
        }
    }

    private long safeAdd(long base, long increment) {
        if (increment > Long.MAX_VALUE - base) {
            return Long.MAX_VALUE;
        }

        return base + increment;
    }

    private record RunFailure(long durationMillis, String errorMessage) {
    }

    private record Candidate(
            Long botId,
            ScheduledJobType jobType,
            long dueAtEpochMs
    ) {
    }

    private static final class BotSchedule {
        private boolean enabled;
        private boolean hasActiveNegotiations;
        private ScheduledJobType workingJobType;
        private long nextCatalogAtEpochMs;
        private long nextNegotiationAtEpochMs = NEVER;
        private long nextPriceProbeAtEpochMs = NEVER;
        private int catalogFailureCount;
        private int negotiationFailureCount;
        private int priceProbeFailureCount;
    }
}

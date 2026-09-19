package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.model.RunningBotDto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class WorkerManager implements AutoCloseable {

    private final WorkerRuntimeConfig config =
            WorkerRuntimeConfig.fromEnvironment();

    private final BotApiClient botApiClient =
            new BotApiClient();

    private final RuntimeTelemetryReporter telemetryReporter =
            new RuntimeTelemetryReporter();

    private final BotRunScheduler scheduler =
            new BotRunScheduler(
                    config,
                    telemetryReporter
            );

    private final BotSessionPreviewRegistry sessionPreviewRegistry =
            new BotSessionPreviewRegistry();

    private final SessionPreviewManager sessionPreviewManager =
            new SessionPreviewManager();

    private final ScheduledExecutorService syncExecutor =
            Executors.newSingleThreadScheduledExecutor(
                    namedThreadFactory("flipbot-scheduler-sync-")
            );

    private final ExecutorService slotExecutor =
            Executors.newFixedThreadPool(
                    config.workerCount(),
                    namedThreadFactory("flipbot-worker-slot-")
            );

    private final List<WorkerSlotHandle> slotHandles =
            new ArrayList<>();

    private final AtomicBoolean started =
            new AtomicBoolean(false);

    private final AtomicBoolean stopping =
            new AtomicBoolean(false);

    /*
     * Slot labels are monotonic for the lifetime of the manager. If a worker
     * thread dies and is replaced, the replacement receives a new number so
     * logs never make two different worker lifetimes look like the same slot.
     */
    private int nextSlotNumber = 1;

    public void start() {
        if (!started.compareAndSet(false, true)) {
            log.warn("WorkerManager is already started.");
            return;
        }

        log.info(
                "Starting scheduler runtime. Max worker slots={}, scheduler headless={}, sync={}s, "
                        + "negotiation check={}s, catalog scan={}s, failure retry={}s, rate-limit retry={}s.",
                config.workerCount(),
                config.schedulerHeadless(),
                config.syncIntervalSeconds(),
                config.negotiationCheckIntervalSeconds(),
                config.catalogScanIntervalSeconds(),
                config.failureDelaySeconds(),
                config.rateLimitDelaySeconds()
        );

        /*
         * Worker slots are not started eagerly. Every scheduler sync computes
         * the required capacity from the current RUNNING bot count and
         * reconciles the worker pool in both directions.
         *
         * Scale-up is immediate (up to configured max). Scale-down is graceful:
         * surplus slots are marked for retirement, finish a job already claimed
         * by that slot, release their BrowserManager on their own worker thread,
         * and then stop. This avoids interrupting a real marketplace action or
         * closing Playwright from a foreign thread.
         */
        syncExecutor.scheduleWithFixedDelay(
                this::syncRunningBots,
                0L,
                config.syncIntervalSeconds(),
                TimeUnit.SECONDS
        );
    }

    private void syncRunningBots() {
        if (stopping.get()) {
            return;
        }

        try {
            List<RunningBotDto> runningBotDtos =
                    botApiClient.getRunningBots()
                            .stream()
                            .filter(
                                    bot -> bot.getId() != null
                                            && bot.getId() > 0
                            )
                            .toList();

            Set<Long> previewRequestedBotIds =
                    runningBotDtos.stream()
                            .filter(RunningBotDto::isSessionPreviewRequested)
                            .map(RunningBotDto::getId)
                            .collect(Collectors.toSet());

            /*
             * Closing ownership comes first. A bot whose preview was disabled
             * must not be unpaused until its visible Chromium has actually
             * exited on the preview owner thread.
             */
            sessionPreviewManager.stopUnrequested(
                    previewRequestedBotIds
            );

            Set<Long> schedulerPausedBotIds =
                    new HashSet<>(previewRequestedBotIds);
            schedulerPausedBotIds.addAll(
                    sessionPreviewManager.activeBotIds()
            );

            scheduler.setPausedBotIds(
                    schedulerPausedBotIds
            );

            sessionPreviewRegistry.replaceFrom(runningBotDtos);

            Map<Long, Boolean> runningBots =
                    runningBotDtos.stream()
                            .collect(
                                    Collectors.toMap(
                                            RunningBotDto::getId,
                                            RunningBotDto::hasActiveNegotiations,
                                            (left, right) -> left
                                    )
                            );

            scheduler.reconcileRunningBots(runningBots);

            /*
             * Start only after scheduler pause ownership is installed. If a
             * normal job was already WORKING at click time, it finishes first;
             * the next sync opens the preview instead of racing the job.
             */
            sessionPreviewManager.startRequestedWhenSafe(
                    previewRequestedBotIds,
                    scheduler
            );

            int requiredSlots = Math.min(
                    config.workerCount(),
                    runningBots.size()
            );

            ensureWorkerSlots(requiredSlots);

            long activeNegotiationBots =
                    runningBots.values()
                            .stream()
                            .filter(Boolean::booleanValue)
                            .count();

            log.info(
                    "[SCHEDULER] Sync complete. RUNNING={}, activeNegotiationBots={}, "
                            + "queued={}, working={}, targetSlots={}, activeSlots={}, retiringSlots={}, startedSlots={}, maxSlots={}.",
                    scheduler.enabledBotCount(),
                    activeNegotiationBots,
                    scheduler.queuedCount(),
                    scheduler.workingCount(),
                    requiredSlots,
                    currentAvailableSlotCount(),
                    currentRetiringSlotCount(),
                    currentStartedSlotCount(),
                    config.workerCount()
            );
        } catch (Exception exception) {
            log.error(
                    "Failed to synchronize RUNNING bots with scheduler.",
                    exception
            );
        }
    }

    private synchronized void ensureWorkerSlots(int requiredSlotCount) {
        int targetSlotCount = Math.max(
                0,
                Math.min(
                        requiredSlotCount,
                        config.workerCount()
                )
        );

        int beforeCleanup = slotHandles.size();

        slotHandles.removeIf(
                handle -> handle.future().isDone()
                        || handle.future().isCancelled()
        );

        int removed = beforeCleanup - slotHandles.size();

        if (removed > 0) {
            log.info(
                    "[SCHEDULER] Reaped {} stopped worker slot(s).",
                    removed
            );
        }

        WorkerSlotCapacityPlan plan = WorkerSlotCapacityPlan.between(
                availableSlotCountUnsafe(),
                targetSlotCount
        );

        if (plan.retireCount() > 0) {
            int remainingToRetire = plan.retireCount();

            /*
             * Retire newest capacity first. This keeps long-lived slot labels
             * stable in logs while still making the choice deterministic.
             */
            for (int index = slotHandles.size() - 1;
                 index >= 0 && remainingToRetire > 0;
                 index--) {

                WorkerSlotHandle handle = slotHandles.get(index);

                if (!isLive(handle)
                        || handle.slot().isRetirementRequested()) {
                    continue;
                }

                if (handle.slot().requestRetirement()) {
                    remainingToRetire--;

                    log.info(
                            "[SCHEDULER] Worker slot {} marked for graceful retirement. targetSlots={}, activeSlotsAfterRequest={}, retiringSlots={}.",
                            handle.slotNumber(),
                            targetSlotCount,
                            availableSlotCountUnsafe(),
                            retiringSlotCountUnsafe()
                    );
                }
            }
        }

        int slotsToStart = WorkerSlotCapacityPlan.between(
                availableSlotCountUnsafe(),
                targetSlotCount
        ).startCount();

        for (int index = 0; index < slotsToStart; index++) {
            int slotNumber = nextSlotNumber++;

            BotWorkerSlot slot =
                    new BotWorkerSlot(
                            slotNumber,
                            scheduler,
                            config,
                            telemetryReporter,
                            sessionPreviewRegistry
                    );

            Future<?> future = slotExecutor.submit(slot);

            slotHandles.add(
                    new WorkerSlotHandle(
                            slotNumber,
                            slot,
                            future
                    )
            );

            log.info(
                    "[SCHEDULER] Started worker slot {}. activeSlots={}/{}, targetSlots={}.",
                    slotNumber,
                    availableSlotCountUnsafe(),
                    config.workerCount(),
                    targetSlotCount
            );
        }
    }

    private synchronized int currentStartedSlotCount() {
        return (int) slotHandles.stream()
                .filter(this::isLive)
                .count();
    }

    private synchronized int currentRetiringSlotCount() {
        return retiringSlotCountUnsafe();
    }

    private synchronized int currentAvailableSlotCount() {
        return availableSlotCountUnsafe();
    }

    private int retiringSlotCountUnsafe() {
        return (int) slotHandles.stream()
                .filter(this::isLive)
                .filter(handle -> handle.slot().isRetirementRequested())
                .count();
    }

    private int availableSlotCountUnsafe() {
        return (int) slotHandles.stream()
                .filter(this::isLive)
                .filter(handle -> !handle.slot().isRetirementRequested())
                .count();
    }

    private boolean isLive(WorkerSlotHandle handle) {
        return !handle.future().isDone()
                && !handle.future().isCancelled();
    }

    public void stop() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        log.info(
                "Stopping scheduler runtime. RUNNING={}, queued={}, working={}, activeSlots={}, retiringSlots={}, startedSlots={}.",
                scheduler.enabledBotCount(),
                scheduler.queuedCount(),
                scheduler.workingCount(),
                currentAvailableSlotCount(),
                currentRetiringSlotCount(),
                currentStartedSlotCount()
        );

        scheduler.shutdown();
        sessionPreviewRegistry.clear();
        syncExecutor.shutdownNow();

        /*
         * Preview runtimes own Playwright objects on their own threads. Ask
         * them to close themselves before worker threads are interrupted.
         */
        sessionPreviewManager.close();

        slotHandles.forEach(handle -> handle.future().cancel(true));
        slotExecutor.shutdownNow();

        awaitTermination(syncExecutor, "scheduler synchronization executor");
        awaitTermination(slotExecutor, "worker slot executor");

        slotHandles.clear();
        telemetryReporter.close();

        log.info("Scheduler runtime stopped.");
    }

    @Override
    public void close() {
        stop();
    }

    private void awaitTermination(
            ExecutorService executor,
            String executorName
    ) {
        try {
            if (!executor.awaitTermination(
                    config.shutdownTimeoutSeconds(),
                    TimeUnit.SECONDS
            )) {
                log.warn(
                        "{} did not terminate within {} seconds.",
                        executorName,
                        config.shutdownTimeoutSeconds()
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "Interrupted while waiting for {} to terminate.",
                    executorName
            );
        }
    }

    private static java.util.concurrent.ThreadFactory namedThreadFactory(
            String prefix
    ) {
        AtomicInteger sequence = new AtomicInteger(1);

        return runnable -> {
            Thread thread = new Thread(
                    runnable,
                    prefix + sequence.getAndIncrement()
            );
            thread.setDaemon(false);
            return thread;
        };
    }

    private record WorkerSlotHandle(
            int slotNumber,
            BotWorkerSlot slot,
            Future<?> future
    ) {
    }
}

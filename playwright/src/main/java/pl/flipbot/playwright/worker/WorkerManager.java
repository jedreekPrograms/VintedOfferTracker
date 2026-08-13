package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.model.RunningBotDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private final ScheduledExecutorService syncExecutor =
            Executors.newSingleThreadScheduledExecutor(
                    namedThreadFactory("flipbot-scheduler-sync-")
            );

    private final ExecutorService slotExecutor =
            Executors.newFixedThreadPool(
                    config.workerCount(),
                    namedThreadFactory("flipbot-worker-slot-")
            );

    private final List<Future<?>> slotFutures =
            new ArrayList<>();

    private final AtomicBoolean started =
            new AtomicBoolean(false);

    private final AtomicBoolean stopping =
            new AtomicBoolean(false);

    private int startedSlotCount = 0;

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
         * Worker slots are deliberately NOT started here.
         * The first scheduler sync knows how many bots are actually RUNNING,
         * and starts only min(RUNNING, configured max) consumers. This avoids
         * waking ten independent Chrome runtimes for two bots and prevents the
         * same tiny bot fleet from gradually migrating through unused slots.
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
            Map<Long, Boolean> runningBots =
                    botApiClient.getRunningBots()
                            .stream()
                            .filter(
                                    bot -> bot.getId() != null
                                            && bot.getId() > 0
                            )
                            .collect(
                                    Collectors.toMap(
                                            RunningBotDto::getId,
                                            RunningBotDto::hasActiveNegotiations,
                                            (left, right) -> left
                                    )
                            );

            scheduler.reconcileRunningBots(runningBots);

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
                            + "queued={}, working={}, activeSlots={}, maxSlots={}.",
                    scheduler.enabledBotCount(),
                    activeNegotiationBots,
                    scheduler.queuedCount(),
                    scheduler.workingCount(),
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

        while (startedSlotCount < targetSlotCount) {
            int slotNumber = ++startedSlotCount;

            BotWorkerSlot slot =
                    new BotWorkerSlot(
                            slotNumber,
                            scheduler,
                            config,
                            telemetryReporter
                    );

            slotFutures.add(slotExecutor.submit(slot));

            log.info(
                    "[SCHEDULER] Started worker slot {}/{} because current RUNNING bot count requires it.",
                    slotNumber,
                    config.workerCount()
            );
        }
    }

    private synchronized int currentStartedSlotCount() {
        return startedSlotCount;
    }

    public void stop() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        log.info(
                "Stopping scheduler runtime. RUNNING={}, queued={}, working={}, activeSlots={}.",
                scheduler.enabledBotCount(),
                scheduler.queuedCount(),
                scheduler.workingCount(),
                currentStartedSlotCount()
        );

        scheduler.shutdown();
        syncExecutor.shutdownNow();

        slotFutures.forEach(future -> future.cancel(true));
        slotExecutor.shutdownNow();

        awaitTermination(syncExecutor, "scheduler synchronization executor");
        awaitTermination(slotExecutor, "worker slot executor");

        slotFutures.clear();
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
}

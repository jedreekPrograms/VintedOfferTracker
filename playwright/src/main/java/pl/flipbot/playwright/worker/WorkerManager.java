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

    public void start() {
        if (!started.compareAndSet(false, true)) {
            log.warn("WorkerManager is already started.");
            return;
        }

        log.info(
                "Starting scheduler runtime. Worker slots={}, sync={}s, "
                        + "negotiation check={}s, catalog scan={}s, "
                        + "failure retry={}s, rate-limit retry={}s.",
                config.workerCount(),
                config.syncIntervalSeconds(),
                config.negotiationCheckIntervalSeconds(),
                config.catalogScanIntervalSeconds(),
                config.failureDelaySeconds(),
                config.rateLimitDelaySeconds()
        );

        startWorkerSlots();

        syncExecutor.scheduleWithFixedDelay(
                this::syncRunningBots,
                0L,
                config.syncIntervalSeconds(),
                TimeUnit.SECONDS
        );
    }

    private void startWorkerSlots() {
        for (int slotNumber = 1; slotNumber <= config.workerCount(); slotNumber++) {
            BotWorkerSlot slot =
                    new BotWorkerSlot(
                            slotNumber,
                            scheduler,
                            config,
                            telemetryReporter
                    );

            slotFutures.add(slotExecutor.submit(slot));
        }
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

            long activeNegotiationBots =
                    runningBots.values()
                            .stream()
                            .filter(Boolean::booleanValue)
                            .count();

            log.info(
                    "[SCHEDULER] Sync complete. RUNNING={}, activeNegotiationBots={}, "
                            + "queued={}, working={}, slots={}.",
                    scheduler.enabledBotCount(),
                    activeNegotiationBots,
                    scheduler.queuedCount(),
                    scheduler.workingCount(),
                    config.workerCount()
            );
        } catch (Exception exception) {
            log.error(
                    "Failed to synchronize RUNNING bots with scheduler.",
                    exception
            );
        }
    }

    public void stop() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        log.info(
                "Stopping scheduler runtime. RUNNING={}, queued={}, working={}.",
                scheduler.enabledBotCount(),
                scheduler.queuedCount(),
                scheduler.workingCount()
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

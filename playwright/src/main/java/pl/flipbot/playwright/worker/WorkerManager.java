package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.model.RunningBotDto;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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

    private static final int MAX_CONCURRENT_BOTS =
            10;

    private static final long SYNC_INTERVAL_SECONDS =
            5L;

    private static final long SHUTDOWN_TIMEOUT_SECONDS =
            20L;


    private final BotApiClient botApiClient =
            new BotApiClient();


    private final ScheduledExecutorService syncExecutor =
            Executors.newSingleThreadScheduledExecutor(
                    namedThreadFactory(
                            "flipbot-worker-sync-"
                    )
            );


    private final ExecutorService workerExecutor =
            Executors.newFixedThreadPool(
                    MAX_CONCURRENT_BOTS,
                    namedThreadFactory(
                            "flipbot-bot-worker-"
                    )
            );


    private final Map<Long, WorkerHandle> workers =
            new ConcurrentHashMap<>();


    private final AtomicBoolean started =
            new AtomicBoolean(false);

    private final AtomicBoolean stopping =
            new AtomicBoolean(false);


    public void start() {

        if (
                !started.compareAndSet(
                        false,
                        true
                )
        ) {

            log.warn(
                    "WorkerManager is already started."
            );

            return;
        }


        log.info(
                "Starting WorkerManager. Max concurrent bots: {}, sync interval: {} seconds.",
                MAX_CONCURRENT_BOTS,
                SYNC_INTERVAL_SECONDS
        );


        syncExecutor.scheduleWithFixedDelay(
                this::syncWorkers,
                0,
                SYNC_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }


    public void syncWorkers() {

        if (
                stopping.get()
        ) {

            return;
        }


        try {

            Set<Long> runningBotIds =
                    botApiClient.getRunningBots()
                            .stream()
                            .map(
                                    RunningBotDto::getId
                            )
                            .collect(
                                    Collectors.toSet()
                            );


            requestStopForInactiveWorkers(
                    runningBotIds
            );

            removeFullyFinishedWorkers();

            startMissingWorkers(
                    runningBotIds
            );

        } catch (Exception exception) {

            log.error(
                    "Failed to synchronize bot workers",
                    exception
            );
        }
    }


    public void stop() {

        if (
                !stopping.compareAndSet(
                        false,
                        true
                )
        ) {

            return;
        }


        log.info(
                "Stopping WorkerManager. Active/queued workers: {}.",
                workers.size()
        );


        syncExecutor.shutdownNow();


        workers.forEach(
                (
                        botId,
                        handle
                ) -> {

                    boolean cancellationRequested =
                            handle.future()
                                    .cancel(
                                            true
                                    );


                    log.info(
                            "Shutdown cancellation requested for bot {}: {}.",
                            botId,
                            cancellationRequested
                    );
                }
        );


        workerExecutor.shutdownNow();


        awaitTermination(
                syncExecutor,
                "worker synchronization executor"
        );

        awaitTermination(
                workerExecutor,
                "bot worker executor"
        );


        workers.clear();


        log.info(
                "WorkerManager stopped."
        );
    }


    @Override
    public void close() {

        stop();
    }


    private void startMissingWorkers(
            Set<Long> runningBotIds
    ) {

        for (
                Long botId
                : runningBotIds
        ) {

            if (
                    stopping.get()
            ) {

                return;
            }


            if (
                    botId == null
                            || botId <= 0
            ) {

                log.warn(
                        "Ignoring invalid running bot ID returned by backend: {}.",
                        botId
                );

                continue;
            }


            if (
                    workers.containsKey(
                            botId
                    )
            ) {

                continue;
            }


            try {

                BotDetailsDto bot =
                        botApiClient.getBot(
                                botId
                        );


                BotWorkerRuntime runtime =
                        new BotWorkerRuntime(
                                bot
                        );


                Future<?> future =
                        workerExecutor.submit(
                                runtime
                        );


                WorkerHandle handle =
                        new WorkerHandle(
                                runtime,
                                future
                        );


                WorkerHandle previous =
                        workers.putIfAbsent(
                                botId,
                                handle
                        );


                if (
                        previous != null
                ) {

                    future.cancel(
                            true
                    );


                    log.warn(
                            "A worker for bot {} appeared concurrently. The duplicate task was cancelled.",
                            botId
                    );

                    continue;
                }


                log.info(
                        "Started isolated worker runtime for bot {}.",
                        botId
                );

            } catch (Exception exception) {

                log.error(
                        "Could not start worker for bot {}. Other bots will still be synchronized.",
                        botId,
                        exception
                );
            }
        }
    }


    private void requestStopForInactiveWorkers(
            Set<Long> runningBotIds
    ) {

        workers.forEach(
                (
                        botId,
                        handle
                ) -> {

                    if (
                            runningBotIds.contains(
                                    botId
                            )
                    ) {

                        return;
                    }


                    Future<?> future =
                            handle.future();


                    if (
                            future.isCancelled()
                                    || handle.runtime().isFinished()
                    ) {

                        return;
                    }


                    boolean cancellationRequested =
                            future.cancel(
                                    true
                            );


                    log.info(
                            "Bot {} is no longer RUNNING. Worker cancellation requested: {}. "
                                    + "The handle will remain registered until the runtime really finishes.",
                            botId,
                            cancellationRequested
                    );
                }
        );
    }


    private void removeFullyFinishedWorkers() {

        workers.entrySet()
                .removeIf(
                        entry -> {

                            WorkerHandle handle =
                                    entry.getValue();


                            boolean runtimeFinished =
                                    handle.runtime()
                                            .isFinished();

                            boolean cancelledBeforeStart =
                                    handle.future()
                                            .isCancelled()
                                            && !handle.runtime()
                                            .isStarted();


                            if (
                                    !runtimeFinished
                                            && !cancelledBeforeStart
                            ) {

                                return false;
                            }


                            log.info(
                                    "Removing fully finished worker handle for bot {}. "
                                            + "Backend state will decide whether it should be restarted.",
                                    entry.getKey()
                            );


                            return true;
                        }
                );
    }


    private void awaitTermination(
            ExecutorService executor,
            String executorName
    ) {

        try {

            if (
                    !executor.awaitTermination(
                            SHUTDOWN_TIMEOUT_SECONDS,
                            TimeUnit.SECONDS
                    )
            ) {

                log.warn(
                        "{} did not terminate within {} seconds.",
                        executorName,
                        SHUTDOWN_TIMEOUT_SECONDS
                );
            }

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();


            log.warn(
                    "Interrupted while waiting for {} to terminate.",
                    executorName
            );
        }
    }


    private static java.util.concurrent.ThreadFactory namedThreadFactory(
            String prefix
    ) {

        AtomicInteger sequence =
                new AtomicInteger(1);


        return runnable -> {

            Thread thread =
                    new Thread(
                            runnable,
                            prefix
                                    + sequence.getAndIncrement()
                    );


            thread.setDaemon(
                    false
            );


            return thread;
        };
    }


    private record WorkerHandle(
            BotWorkerRuntime runtime,
            Future<?> future
    ) {
    }
}

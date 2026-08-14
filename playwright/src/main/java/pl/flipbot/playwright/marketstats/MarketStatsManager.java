package pl.flipbot.playwright.marketstats;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class MarketStatsManager implements AutoCloseable {

    private static final long INITIAL_DELAY_SECONDS = 30L;
    private static final long OBSERVER_POLL_SECONDS = 60L;
    private static final long FAILURE_RETRY_MINUTES = 15L;

    private final MarketStatsRuntimeConfig config =
            MarketStatsRuntimeConfig.fromEnvironment();

    private final MarketStatsApiClient apiClient =
            new MarketStatsApiClient();

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(
                    runnable -> {
                        Thread thread = new Thread(
                                runnable,
                                "flipbot-market-stats"
                        );
                        thread.setDaemon(false);
                        return thread;
                    }
            );

    private final AtomicBoolean started =
            new AtomicBoolean(false);

    private final AtomicBoolean stopping =
            new AtomicBoolean(false);

    private volatile long nextAttemptAtMillis = 0L;

    public void start() {
        if (!config.enabled()) {
            log.info(
                    "[MARKET STATS] Collector is disabled by FLIPBOT_MARKET_STATS_ENABLED=false."
            );
            return;
        }

        if (!started.compareAndSet(false, true)) {
            return;
        }

        log.info(
                "[MARKET STATS] Dedicated collector is enabled. Observer is managed by the frontend. "
                        + "First check in {}s, completed scans every {}h.",
                INITIAL_DELAY_SECONDS,
                config.intervalHours()
        );

        executor.scheduleWithFixedDelay(
                this::pollSafely,
                INITIAL_DELAY_SECONDS,
                OBSERVER_POLL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private void pollSafely() {
        if (stopping.get()) {
            return;
        }

        long now = System.currentTimeMillis();

        if (now < nextAttemptAtMillis) {
            return;
        }

        try {
            new MarketStatsCollector(
                    config,
                    apiClient
            ).collectOnce();

            nextAttemptAtMillis =
                    System.currentTimeMillis()
                            + TimeUnit.HOURS.toMillis(
                            config.intervalHours()
                    );

            log.info(
                    "[MARKET STATS] Collection completed. Next full scan in {}h.",
                    config.intervalHours()
            );
        } catch (Exception exception) {
            String message = exception.getMessage();

            if (message != null
                    && message.contains(
                    "observer is not configured yet"
            )) {
                log.info(
                        "[MARKET STATS] No observer is configured yet. "
                                + "Create it on the Bots page; the collector will discover it automatically."
                );

                nextAttemptAtMillis =
                        System.currentTimeMillis()
                                + TimeUnit.MINUTES.toMillis(1L);
                return;
            }

            nextAttemptAtMillis =
                    System.currentTimeMillis()
                            + TimeUnit.MINUTES.toMillis(
                            FAILURE_RETRY_MINUTES
                    );

            log.error(
                    "[MARKET STATS] Collection failed. The normal bot scheduler is unaffected; "
                            + "the collector will retry in {} minutes.",
                    FAILURE_RETRY_MINUTES,
                    exception
            );
        }
    }

    public void stop() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        executor.shutdownNow();

        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn(
                        "[MARKET STATS] Collector executor did not terminate within 10 seconds."
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        stop();
    }
}

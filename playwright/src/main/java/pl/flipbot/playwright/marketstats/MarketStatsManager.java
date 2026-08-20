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
    private volatile boolean failureBackoffActive = false;

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
            if (failureBackoffActive) {
                return;
            }

            try {
                if (!apiClient.isScanNeeded()) {
                    return;
                }

                log.info(
                        "[MARKET STATS] A model is waiting for a fresh baseline. "
                                + "Starting collection before the normal {}h interval.",
                        config.intervalHours()
                );
            } catch (Exception exception) {
                log.warn(
                        "[MARKET STATS] Could not check whether an early baseline scan is needed. Keeping the normal schedule. reason={}",
                        friendlyMessage(exception)
                );
                log.debug(
                        "[MARKET STATS] Full early-baseline check error.",
                        exception
                );
                return;
            }
        }

        try {
            new MarketStatsCollector(
                    config,
                    apiClient
            ).collectOnce();

            failureBackoffActive = false;
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

                failureBackoffActive = false;
                nextAttemptAtMillis =
                        System.currentTimeMillis()
                                + TimeUnit.MINUTES.toMillis(1L);
                return;
            }

            failureBackoffActive = true;
            nextAttemptAtMillis =
                    System.currentTimeMillis()
                            + TimeUnit.MINUTES.toMillis(
                            FAILURE_RETRY_MINUTES
                    );

            log.error(
                    "[MARKET STATS] Collection failed. Normal bot scheduling is unaffected. Retry in {} minutes. reason={}",
                    FAILURE_RETRY_MINUTES,
                    friendlyMessage(exception)
            );
            log.debug(
                    "[MARKET STATS] Full collection failure.",
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

    private String friendlyMessage(Throwable exception) {
        if (exception == null
                || exception.getMessage() == null
                || exception.getMessage().isBlank()) {
            return exception == null
                    ? "unknown error"
                    : exception.getClass().getSimpleName();
        }

        return exception.getMessage()
                .lines()
                .findFirst()
                .orElse(exception.getMessage())
                .trim();
    }
}

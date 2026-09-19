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
    private static final long FAILURE_RETRY_MINUTES = 30L;

    private final MarketStatsRuntimeConfig config =
            MarketStatsRuntimeConfig.fromEnvironment();

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
    private volatile int nextTargetStartIndex = 0;

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
                        + "First check in {}s. After every pass it respects at least {}m normal cooldown; "
                        + "failed/rate-limited passes back off for {}m. Rate-limited passes resume from the model after the one that triggered backoff, so one large model cannot starve the rest of the queue. "
                        + "Observer Chromium is recycled after at most {} model target(s) to cap browser peak memory.",
                INITIAL_DELAY_SECONDS,
                config.refreshCooldownMinutes(),
                FAILURE_RETRY_MINUTES,
                config.browserRecycleTargetCount()
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

        long startedAtMillis = System.currentTimeMillis();
        int passStartIndex = nextTargetStartIndex;
        ResumableMarketStatsApiClient apiClient = null;

        try {
            int batchStartIndex = passStartIndex;
            int attemptedTargets = 0;
            int totalTargets = -1;
            int batchNumber = 0;

            while (!stopping.get()) {
                int remainingTargets = totalTargets < 0
                        ? config.browserRecycleTargetCount()
                        : Math.min(
                                config.browserRecycleTargetCount(),
                                Math.max(1, totalTargets - attemptedTargets)
                        );

                apiClient = new ResumableMarketStatsApiClient(
                        batchStartIndex,
                        remainingTargets
                );
                batchNumber++;

                log.info(
                        "[MARKET STATS] Starting browser batch {} from global target index {} with capacity {}. A fresh observer Chromium/context will be used for this batch.",
                        batchNumber,
                        batchStartIndex,
                        remainingTargets
                );

                new MarketStatsCollector(config, apiClient).collectOnce();

                int returnedTargets = apiClient.returnedTargetCount();
                totalTargets = apiClient.targetCount();
                attemptedTargets += returnedTargets;

                if (totalTargets <= 0 || returnedTargets <= 0) {
                    break;
                }

                if (attemptedTargets >= totalTargets) {
                    break;
                }

                int nextBatchStartIndex =
                        apiClient.resumeIndexAfterCurrentTarget();

                if (nextBatchStartIndex == batchStartIndex) {
                    throw new IllegalStateException(
                            "Market-stats browser batching made no target-index progress. startIndex="
                                    + batchStartIndex
                                    + ", attempted="
                                    + attemptedTargets
                                    + ", total="
                                    + totalTargets
                    );
                }

                log.info(
                        "[MARKET STATS] Browser batch {} completed after {} target(s). Its Chromium runtime is now closed; continuing from global target index {} with a fresh runtime. attempted={}/{}.",
                        batchNumber,
                        returnedTargets,
                        nextBatchStartIndex,
                        attemptedTargets,
                        totalTargets
                );

                batchStartIndex = nextBatchStartIndex;
            }

            if (stopping.get()) {
                return;
            }

            long completedAtMillis = System.currentTimeMillis();
            long durationSeconds = Math.max(
                    0L,
                    TimeUnit.MILLISECONDS.toSeconds(
                            completedAtMillis - startedAtMillis
                    )
            );

            nextTargetStartIndex = 0;
            nextAttemptAtMillis =
                    completedAtMillis
                            + TimeUnit.MINUTES.toMillis(
                            config.refreshCooldownMinutes()
                    );

            log.info(
                    "[MARKET STATS] Collection completed in {}s across {} browser batch(es). All {} model target(s) were attempted; the next full pass returns to the normal target order after {}m cooldown.",
                    durationSeconds,
                    batchNumber,
                    Math.max(0, totalTargets),
                    config.refreshCooldownMinutes()
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

            if (containsTrafficBackoffMarker(exception)) {
                int previousStartIndex = nextTargetStartIndex;
                nextTargetStartIndex = apiClient == null
                        ? previousStartIndex
                        : apiClient.resumeIndexAfterCurrentTarget();
                nextAttemptAtMillis =
                        System.currentTimeMillis()
                                + TimeUnit.MINUTES.toMillis(
                                FAILURE_RETRY_MINUTES
                        );

                log.warn(
                        "[MARKET STATS] Vinted requested traffic backoff. Pausing observer traffic for {} minutes. "
                                + "This pass started at target index {} and the next pass will resume at target index {}, after the model that triggered backoff. "
                                + "Normal bot scheduling is unaffected. reason={}",
                        FAILURE_RETRY_MINUTES,
                        previousStartIndex,
                        nextTargetStartIndex,
                        friendlyMessage(exception)
                );
                log.debug(
                        "[MARKET STATS] Full traffic-backoff failure.",
                        exception
                );
                return;
            }

            nextAttemptAtMillis =
                    System.currentTimeMillis()
                            + TimeUnit.MINUTES.toMillis(
                            FAILURE_RETRY_MINUTES
                    );

            log.error(
                    "[MARKET STATS] Collection failed. Normal bot scheduling is unaffected. Retry in {} minutes from target index {}. reason={}",
                    FAILURE_RETRY_MINUTES,
                    nextTargetStartIndex,
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

    private boolean containsTrafficBackoffMarker(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            String message = current.getMessage();
            if (message != null
                    && message.contains(
                    MarketListingPublishedAtResolver.TRAFFIC_BACKOFF_MARKER
            )) {
                return true;
            }
            current = current.getCause();
        }

        return false;
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

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
                        + "failed/rate-limited passes back off for {}m. The collector is single-threaded, so long passes never overlap.",
                INITIAL_DELAY_SECONDS,
                config.refreshCooldownMinutes(),
                FAILURE_RETRY_MINUTES
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

        /*
         * Do not bypass the configured cooldown just because a baseline or
         * publication backfill is still incomplete. The observer is auxiliary
         * read-only traffic and must not repeatedly hammer Vinted while normal
         * bot jobs are running from the same machine/network.
         */
        if (now < nextAttemptAtMillis) {
            return;
        }

        long startedAtMillis = System.currentTimeMillis();

        try {
            new MarketStatsCollector(
                    config,
                    new MarketStatsApiClient()
            ).collectOnce();

            long completedAtMillis = System.currentTimeMillis();
            long durationSeconds = Math.max(
                    0L,
                    TimeUnit.MILLISECONDS.toSeconds(
                            completedAtMillis - startedAtMillis
                    )
            );

            nextAttemptAtMillis =
                    completedAtMillis
                            + TimeUnit.MINUTES.toMillis(
                            config.refreshCooldownMinutes()
                    );

            log.info(
                    "[MARKET STATS] Collection completed in {}s. Next full pass may start after {}m cooldown. "
                            + "Effective start-to-start spacing automatically includes the duration of this pass.",
                    durationSeconds,
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

            nextAttemptAtMillis =
                    System.currentTimeMillis()
                            + TimeUnit.MINUTES.toMillis(
                            FAILURE_RETRY_MINUTES
                    );

            log.error(
                    "[MARKET STATS] Collection failed or Vinted requested backoff. Normal bot scheduling is unaffected. Retry in {} minutes. reason={}",
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

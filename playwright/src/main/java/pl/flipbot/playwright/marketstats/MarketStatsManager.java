package pl.flipbot.playwright.marketstats;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class MarketStatsManager implements AutoCloseable {

    private static final long INITIAL_DELAY_SECONDS = 30L;

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

    public void start() {
        if (!config.enabled()) {
            log.info(
                    "[MARKET STATS] Collector is disabled. Set FLIPBOT_MARKET_STATS_ENABLED=true and FLIPBOT_MARKET_STATS_BOT_ID=<id> to enable it."
            );
            return;
        }

        if (!started.compareAndSet(false, true)) {
            return;
        }

        log.info(
                "[MARKET STATS] Scheduling dedicated collector. observerBot={}, firstRunIn={}s, interval={}h.",
                config.observerBotId(),
                INITIAL_DELAY_SECONDS,
                config.intervalHours()
        );

        executor.scheduleWithFixedDelay(
                this::collectSafely,
                INITIAL_DELAY_SECONDS,
                config.intervalHours(),
                TimeUnit.HOURS
        );
    }

    private void collectSafely() {
        if (stopping.get()) {
            return;
        }

        try {
            new MarketStatsCollector(
                    config,
                    apiClient
            ).collectOnce();
        } catch (Exception exception) {
            log.error(
                    "[MARKET STATS] Daily collection failed. The normal bot scheduler is unaffected; the collector will retry on its next interval.",
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

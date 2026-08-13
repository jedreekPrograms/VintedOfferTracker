package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.target.VintedRateLimitException;

import java.util.concurrent.TimeUnit;

@Slf4j
public class BotWorkerSlot implements Runnable {

    private final int slotNumber;
    private final BotRunScheduler scheduler;
    private final WorkerRuntimeConfig config;
    private final RuntimeTelemetryReporter telemetryReporter;

    private final BotApiClient botApiClient =
            new BotApiClient();

    public BotWorkerSlot(
            int slotNumber,
            BotRunScheduler scheduler,
            WorkerRuntimeConfig config,
            RuntimeTelemetryReporter telemetryReporter
    ) {
        this.slotNumber = slotNumber;
        this.scheduler = scheduler;
        this.config = config;
        this.telemetryReporter = telemetryReporter;
    }

    @Override
    public void run() {
        log.info(
                "[SLOT {}] Starting reusable worker slot on thread {}. Browser will launch lazily on first claimed job; headless={}.",
                slotNumber,
                Thread.currentThread().getName(),
                config.schedulerHeadless()
        );

        BrowserManager browserManager = null;

        try {
            while (!Thread.currentThread().isInterrupted()) {
                ScheduledBotTask task = scheduler.takeNext();
                Long botId = task.botId();
                ScheduledJobType jobType = task.jobType();

                long nextDelayMillis =
                        TimeUnit.SECONDS.toMillis(
                                config.normalDelaySeconds(jobType)
                        );

                boolean delayAllJobs = false;
                boolean reportQueuedAfterRun = true;
                long startedAtNanos = System.nanoTime();

                telemetryReporter.runStarted(
                        botId,
                        slotNumber
                );

                try {
                    log.info(
                            "[SLOT {}] Claimed {} for bot {}. Queue={}, working={}.",
                            slotNumber,
                            jobType,
                            botId,
                            scheduler.queuedCount(),
                            scheduler.workingCount()
                    );

                    if (browserManager == null) {
                        log.info(
                                "[SLOT {}] Launching Playwright browser runtime for the first claimed job. headless={}",
                                slotNumber,
                                config.schedulerHeadless()
                        );

                        browserManager = new BrowserManager(
                                config.schedulerHeadless()
                        );
                    }

                    BotDetailsDto bot = botApiClient.getBot(botId);

                    ScheduledBotRunExecutor runExecutor =
                            new ScheduledBotRunExecutor(
                                    bot,
                                    browserManager
                            );

                    runExecutor.executeJob(jobType);

                    long durationMs = elapsedMillis(startedAtNanos);
                    telemetryReporter.runSucceeded(
                            botId,
                            durationMs
                    );

                    log.info(
                            "[SLOT {}] Bot {} completed {} in {} ms. Normal interval={} seconds.",
                            slotNumber,
                            botId,
                            jobType,
                            durationMs,
                            config.normalDelaySeconds(jobType)
                    );

                } catch (VintedRateLimitException exception) {
                    nextDelayMillis =
                            TimeUnit.SECONDS.toMillis(
                                    config.rateLimitDelaySeconds()
                            );
                    delayAllJobs = true;
                    reportQueuedAfterRun = false;

                    long durationMs = elapsedMillis(startedAtNanos);
                    long nextRunAtEpochMs =
                            System.currentTimeMillis() + nextDelayMillis;

                    telemetryReporter.rateLimited(
                            botId,
                            durationMs,
                            nextRunAtEpochMs,
                            errorMessage(exception)
                    );

                    log.warn(
                            "[SLOT {}] Bot {} hit an explicit Vinted rate limit during {}. "
                                    + "All jobs delayed by {} seconds.",
                            slotNumber,
                            botId,
                            jobType,
                            config.rateLimitDelaySeconds()
                    );

                    log.debug(
                            "[SLOT {}] Rate-limit exception for bot {} during {}.",
                            slotNumber,
                            botId,
                            jobType,
                            exception
                    );

                } catch (Exception exception) {
                    nextDelayMillis =
                            TimeUnit.SECONDS.toMillis(
                                    config.failureDelaySeconds()
                            );
                    delayAllJobs = true;
                    reportQueuedAfterRun = false;

                    long durationMs = elapsedMillis(startedAtNanos);
                    long nextRunAtEpochMs =
                            System.currentTimeMillis() + nextDelayMillis;

                    telemetryReporter.runFailed(
                            botId,
                            durationMs,
                            nextRunAtEpochMs,
                            errorMessage(exception)
                    );

                    log.error(
                            "[SLOT {}] Bot {} failed during {}. "
                                    + "All jobs delayed by {} seconds.",
                            slotNumber,
                            botId,
                            jobType,
                            config.failureDelaySeconds(),
                            exception
                    );

                } finally {
                    scheduler.completeRun(
                            botId,
                            jobType,
                            nextDelayMillis,
                            delayAllJobs,
                            reportQueuedAfterRun
                    );
                }
            }

        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.info(
                    "[SLOT {}] Worker slot interrupted.",
                    slotNumber
            );

        } catch (Exception exception) {
            log.error(
                    "[SLOT {}] Worker slot stopped because its runtime failed.",
                    slotNumber,
                    exception
            );

        } finally {
            if (browserManager != null) {
                try {
                    browserManager.close();
                } catch (Exception exception) {
                    log.warn(
                            "[SLOT {}] Could not close Playwright browser runtime cleanly.",
                            slotNumber,
                            exception
                    );
                }
            }

            log.info(
                    "[SLOT {}] Worker slot stopped.",
                    slotNumber
            );
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(
                0L,
                (System.nanoTime() - startedAtNanos) / 1_000_000L
        );
    }

    private String errorMessage(Exception exception) {
        String message = exception.getMessage();

        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }

        return exception.getClass().getSimpleName()
                + ": "
                + message;
    }
}

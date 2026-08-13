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
                "[SLOT {}] Starting reusable worker slot on thread {}.",
                slotNumber,
                Thread.currentThread().getName()
        );

        try (BrowserManager browserManager = new BrowserManager()) {
            while (!Thread.currentThread().isInterrupted()) {
                ScheduledBotTask task = scheduler.takeNext();
                Long botId = task.botId();

                long nextDelayMillis =
                        TimeUnit.SECONDS.toMillis(
                                config.normalRunDelaySeconds()
                        );

                boolean reportQueuedAfterRun = true;
                long startedAtNanos = System.nanoTime();

                telemetryReporter.runStarted(
                        botId,
                        slotNumber
                );

                try {
                    log.info(
                            "[SLOT {}] Claimed bot {}. Queue={}, working={}.",
                            slotNumber,
                            botId,
                            scheduler.queuedCount(),
                            scheduler.workingCount()
                    );

                    BotDetailsDto bot = botApiClient.getBot(botId);

                    ScheduledBotRunExecutor runExecutor =
                            new ScheduledBotRunExecutor(
                                    bot,
                                    browserManager
                            );

                    runExecutor.executeOneRun();

                    long durationMs = elapsedMillis(startedAtNanos);
                    telemetryReporter.runSucceeded(
                            botId,
                            durationMs
                    );

                    log.info(
                            "[SLOT {}] Bot {} completed one scheduled run in {} ms.",
                            slotNumber,
                            botId,
                            durationMs
                    );

                } catch (VintedRateLimitException exception) {
                    nextDelayMillis =
                            TimeUnit.SECONDS.toMillis(
                                    config.rateLimitDelaySeconds()
                            );
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
                            "[SLOT {}] Bot {} hit an explicit Vinted rate limit. "
                                    + "Next run delayed by {} seconds.",
                            slotNumber,
                            botId,
                            config.rateLimitDelaySeconds()
                    );

                    log.debug(
                            "[SLOT {}] Rate-limit exception for bot {}.",
                            slotNumber,
                            botId,
                            exception
                    );

                } catch (Exception exception) {
                    nextDelayMillis =
                            TimeUnit.SECONDS.toMillis(
                                    config.failureDelaySeconds()
                            );
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
                            "[SLOT {}] Bot {} failed during its scheduled run. "
                                    + "Next attempt in {} seconds.",
                            slotNumber,
                            botId,
                            config.failureDelaySeconds(),
                            exception
                    );

                } finally {
                    scheduler.completeRun(
                            botId,
                            nextDelayMillis,
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
                    "[SLOT {}] Worker slot stopped because its Playwright runtime failed.",
                    slotNumber,
                    exception
            );

        } finally {
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

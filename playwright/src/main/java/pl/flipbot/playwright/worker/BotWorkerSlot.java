package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryStateResponse;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.target.VintedRateLimitException;
import pl.flipbot.playwright.target.VintedSessionBlockedException;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BotWorkerSlot implements Runnable {

    private static final long SESSION_BLOCK_FALLBACK_DELAY_MINUTES = 15L;

    private final int slotNumber;
    private final BotRunScheduler scheduler;
    private final WorkerRuntimeConfig config;
    private final RuntimeTelemetryReporter telemetryReporter;

    private final BotApiClient botApiClient = new BotApiClient();

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

                Long persistedBlockDelayMillis = persistedSessionBlockDelay(botId);
                if (persistedBlockDelayMillis != null && persistedBlockDelayMillis > 0L) {
                    log.warn(
                            "[SESSION BLOCK] Bot {} was claimed after a scheduler/process refresh, but its persisted Vinted session cooldown is still active for about {} minute(s). No browser job will start early.",
                            botId,
                            Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(persistedBlockDelayMillis))
                    );
                    scheduler.completeRun(
                            botId,
                            jobType,
                            persistedBlockDelayMillis,
                            true,
                            false
                    );
                    continue;
                }

                long nextDelayMillis = TimeUnit.SECONDS.toMillis(
                        config.normalDelaySeconds(jobType)
                );

                boolean delayAllJobs = false;
                boolean reportQueuedAfterRun = true;
                long startedAtNanos = System.nanoTime();

                telemetryReporter.runStarted(botId, slotNumber);

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
                                "[SLOT {}] Launching reusable Playwright browser runtime for the first claimed job. headless={}",
                                slotNumber,
                                config.schedulerHeadless()
                        );
                        browserManager = new BrowserManager(config.schedulerHeadless());
                    }

                    BotDetailsDto bot = botApiClient.getBot(botId);
                    ScheduledBotRunExecutor runExecutor =
                            new ScheduledBotRunExecutor(bot, browserManager);

                    runExecutor.executeJob(jobType);

                    long durationMs = elapsedMillis(startedAtNanos);
                    telemetryReporter.runSucceeded(botId, durationMs);

                    log.info(
                            "[SLOT {}] Bot {} completed {} in {} ms. Next normal {} interval={} seconds.",
                            slotNumber,
                            botId,
                            jobType,
                            durationMs,
                            jobType,
                            config.normalDelaySeconds(jobType)
                    );

                } catch (VintedSessionBlockedException exception) {
                    delayAllJobs = true;
                    reportQueuedAfterRun = false;

                    long durationMs = elapsedMillis(startedAtNanos);
                    int attemptNumber = 1;

                    try {
                        RuntimeTelemetryReporter.SessionBlockCooldown cooldown =
                                telemetryReporter.sessionBlocked(
                                        botId,
                                        durationMs,
                                        errorMessage(exception)
                                );

                        attemptNumber = cooldown.attemptNumber();
                        nextDelayMillis = Math.max(
                                0L,
                                cooldown.nextRunAtEpochMs() - System.currentTimeMillis()
                        );
                    } catch (Exception telemetryException) {
                        nextDelayMillis = TimeUnit.MINUTES.toMillis(
                                SESSION_BLOCK_FALLBACK_DELAY_MINUTES
                        );
                        log.error(
                                "[SESSION BLOCK] Could not persist/read the exponential cooldown for bot {}. Falling back to {} minutes for safety. reason={}",
                                botId,
                                SESSION_BLOCK_FALLBACK_DELAY_MINUTES,
                                errorMessage(telemetryException)
                        );
                    }

                    log.warn(
                            "[SESSION BLOCK] Bot {} is blocked by Vinted during {}. Detection #{}. All jobs are paused for about {} minute(s). The block episode start is persisted and is NOT reset by retries.",
                            botId,
                            jobType,
                            attemptNumber,
                            Math.max(1L, TimeUnit.MILLISECONDS.toMinutes(nextDelayMillis))
                    );
                    log.debug(
                            "[SESSION BLOCK] Full session-block exception for bot {} during {}.",
                            botId,
                            jobType,
                            exception
                    );

                } catch (VintedRateLimitException exception) {
                    nextDelayMillis = TimeUnit.SECONDS.toMillis(
                            config.rateLimitDelaySeconds()
                    );
                    delayAllJobs = true;
                    reportQueuedAfterRun = false;

                    long durationMs = elapsedMillis(startedAtNanos);
                    long nextRunAtEpochMs = System.currentTimeMillis() + nextDelayMillis;

                    telemetryReporter.rateLimited(
                            botId,
                            durationMs,
                            nextRunAtEpochMs,
                            errorMessage(exception)
                    );

                    log.warn(
                            "[SLOT {}] Bot {} hit an explicit Vinted rate limit during {}. All jobs for this bot are delayed by {} seconds to protect the account.",
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
                    nextDelayMillis = TimeUnit.SECONDS.toMillis(
                            config.failureDelaySeconds()
                    );
                    delayAllJobs = false;
                    reportQueuedAfterRun = false;

                    long durationMs = elapsedMillis(startedAtNanos);
                    long nextRunAtEpochMs = System.currentTimeMillis() + nextDelayMillis;

                    telemetryReporter.runFailed(
                            botId,
                            durationMs,
                            nextRunAtEpochMs,
                            errorMessage(exception)
                    );

                    log.error(
                            "[SLOT {}] Bot {} failed during {}. Only {} will retry in {} seconds; the bot's other scheduled job type keeps its own schedule. reason={}",
                            slotNumber,
                            botId,
                            jobType,
                            jobType,
                            config.failureDelaySeconds(),
                            errorMessage(exception)
                    );
                    log.debug(
                            "[SLOT {}] Full failure for bot {} during {}.",
                            slotNumber,
                            botId,
                            jobType,
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
            log.info("[SLOT {}] Worker slot interrupted.", slotNumber);

        } catch (Exception exception) {
            log.error(
                    "[SLOT {}] Worker slot stopped because its runtime failed. reason={}",
                    slotNumber,
                    errorMessage(exception)
            );
            log.debug(
                    "[SLOT {}] Full worker-slot runtime failure.",
                    slotNumber,
                    exception
            );

        } finally {
            if (browserManager != null) {
                try {
                    browserManager.close();
                } catch (Exception exception) {
                    log.warn(
                            "[SLOT {}] Could not close Playwright browser runtime cleanly. reason={}",
                            slotNumber,
                            errorMessage(exception)
                    );
                    log.debug(
                            "[SLOT {}] Full browser-runtime close error.",
                            slotNumber,
                            exception
                    );
                }
            }

            log.info("[SLOT {}] Worker slot stopped.", slotNumber);
        }
    }

    private Long persistedSessionBlockDelay(Long botId) {
        try {
            RuntimeTelemetryStateResponse state = telemetryReporter.currentState(botId);
            if (state == null
                    || state.sessionBlockedSince() == null
                    || state.nextRunAt() == null) {
                return null;
            }

            long remaining = Instant.parse(state.nextRunAt()).toEpochMilli()
                    - System.currentTimeMillis();
            return Math.max(0L, remaining);
        } catch (Exception exception) {
            log.warn(
                    "[SESSION BLOCK] Could not read persisted cooldown for bot {} before run. Continuing with the in-memory scheduler state. reason={}",
                    botId,
                    errorMessage(exception)
            );
            return null;
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

        String firstLine = message.lines()
                .findFirst()
                .orElse(message)
                .trim();

        return exception.getClass().getSimpleName() + ": " + firstLine;
    }
}

package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryStateResponse;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.browser.BrowserCapacityController;
import pl.flipbot.playwright.browser.BrowserCapacityUnavailableException;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.target.VintedRateLimitException;
import pl.flipbot.playwright.target.VintedSessionBlockedException;
import pl.flipbot.playwright.verification.HumanVerificationRequiredException;

import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class BotWorkerSlot implements Runnable {

    private static final long SESSION_BLOCK_FALLBACK_DELAY_MINUTES = 15L;
    private static final long RETIREMENT_POLL_MILLIS = 1_000L;

    private final int slotNumber;
    private final BotRunScheduler scheduler;
    private final WorkerRuntimeConfig config;
    private final RuntimeTelemetryReporter telemetryReporter;
    private final BotSessionPreviewRegistry sessionPreviewRegistry;

    private final BotApiClient botApiClient = new BotApiClient();
    private final AtomicBoolean retirementRequested = new AtomicBoolean(false);

    public BotWorkerSlot(
            int slotNumber,
            BotRunScheduler scheduler,
            WorkerRuntimeConfig config,
            RuntimeTelemetryReporter telemetryReporter,
            BotSessionPreviewRegistry sessionPreviewRegistry
    ) {
        this.slotNumber = slotNumber;
        this.scheduler = scheduler;
        this.config = config;
        this.telemetryReporter = telemetryReporter;
        this.sessionPreviewRegistry = sessionPreviewRegistry;
    }

    boolean requestRetirement() {
        boolean changed = retirementRequested.compareAndSet(false, true);

        if (changed) {
            log.info(
                    "[SLOT {}] Graceful retirement requested. The slot will not claim another job after its current wait/run completes.",
                    slotNumber
            );
        }

        return changed;
    }

    boolean isRetirementRequested() {
        return retirementRequested.get();
    }

    @Override
    public void run() {
        boolean keepBrowserBetweenJobs =
                WorkerBrowserRetentionPolicy.keepBrowserOpenBetweenJobs(
                        config.schedulerHeadless()
                );

        log.info(
                "[SLOT {}] Starting worker slot on thread {}. Browser will launch lazily on first claimed job; headless={}, reuseBetweenJobs={}, browserIdleTimeout={}s.",
                slotNumber,
                Thread.currentThread().getName(),
                config.schedulerHeadless(),
                keepBrowserBetweenJobs,
                config.browserIdleTimeoutSeconds()
        );

        BrowserManager browserManager = null;
        long browserIdleSinceNanos = 0L;

        try {
            while (!Thread.currentThread().isInterrupted()) {
                if (retirementRequested.get()) {
                    log.info(
                            "[SLOT {}] Graceful retirement starting before another job is claimed.",
                            slotNumber
                    );
                    break;
                }

                if (browserManager != null && keepBrowserBetweenJobs) {
                    long idleTimeoutNanos = TimeUnit.SECONDS.toNanos(
                            config.browserIdleTimeoutSeconds()
                    );
                    long idleElapsedNanos = browserIdleSinceNanos <= 0L
                            ? 0L
                            : Math.max(
                                    0L,
                                    System.nanoTime() - browserIdleSinceNanos
                            );

                    if (browserIdleSinceNanos > 0L
                            && idleElapsedNanos >= idleTimeoutNanos) {
                        browserManager = closeBrowserRuntime(
                                browserManager,
                                "idle timeout after "
                                        + config.browserIdleTimeoutSeconds()
                                        + "s without a ready job"
                        );
                        browserIdleSinceNanos = 0L;
                        continue;
                    }
                }

                long pollTimeoutMillis = RETIREMENT_POLL_MILLIS;

                if (browserManager != null
                        && keepBrowserBetweenJobs
                        && browserIdleSinceNanos > 0L) {
                    long idleTimeoutNanos = TimeUnit.SECONDS.toNanos(
                            config.browserIdleTimeoutSeconds()
                    );
                    long remainingIdleNanos = Math.max(
                            0L,
                            idleTimeoutNanos
                                    - (System.nanoTime() - browserIdleSinceNanos)
                    );
                    long remainingIdleMillis = Math.max(
                            1L,
                            TimeUnit.NANOSECONDS.toMillis(remainingIdleNanos)
                    );

                    pollTimeoutMillis = Math.min(
                            RETIREMENT_POLL_MILLIS,
                            remainingIdleMillis
                    );
                }

                ScheduledBotTask task = scheduler.pollNext(pollTimeoutMillis);

                if (task == null) {
                    continue;
                }

                Long botId = task.botId();
                ScheduledJobType jobType = task.jobType();

                /*
                 * A retirement request can race with pollNext returning a task.
                 * Once the scheduler has claimed a task, this slot must finish
                 * that claim so the bot schedule cannot remain stuck in WORKING.
                 * The retirement flag is checked again before the next claim.
                 */
                BrowserCapacityController.Permit capacityPermit;
                try {
                    capacityPermit = BrowserCapacityController.shared().acquire();
                } catch (BrowserCapacityUnavailableException exception) {
                    // No browser or marketplace action started. Keep this a
                    // queued job, without touching failures or session state.
                    scheduler.completeRun(botId, jobType, 5_000L, false, true);
                    log.debug("[BROWSER CAPACITY] Deferred bot {} / {}: {}", botId, jobType, exception.getMessage());
                    continue;
                }

                // The permit also covers preflight failures and is returned only
                // after the job's finally block has closed its browser runtime.
                try (capacityPermit) {
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
                        browserManager = closeBrowserRuntime(
                                browserManager,
                                "persisted session cooldown before browser job for bot "
                                        + botId
                        );
                        browserIdleSinceNanos = 0L;
                        continue;
                    }

                    long nextDelayMillis = TimeUnit.SECONDS.toMillis(
                            config.normalDelaySeconds(jobType)
                    );

                    boolean delayAllJobs = false;
                    boolean reportQueuedAfterRun = true;
                    long startedAtNanos = System.nanoTime();

                    boolean previewRequested =
                            config.schedulerHeadless()
                                    && sessionPreviewRegistry.isPreviewRequested(botId);
                    boolean jobHeadless =
                            config.schedulerHeadless() && !previewRequested;

                    try {
                        telemetryReporter.runStarted(botId, slotNumber);
                        log.info(
                                "[SLOT {}] Claimed {} for bot {}. Queue={}, working={}, headless={}, sessionPreview={}.",
                                slotNumber,
                                jobType,
                                botId,
                                scheduler.queuedCount(),
                                scheduler.workingCount(),
                                jobHeadless,
                                previewRequested
                        );

                        BotDetailsDto bot = botApiClient.getBot(botId);
                        if (browserManager == null) {
                            log.info(
                                    "[SLOT {}] Launching Playwright browser runtime for claimed job. headless={}, reuseBetweenJobs={}, sessionPreview={}",
                                    slotNumber,
                                    jobHeadless,
                                    keepBrowserBetweenJobs,
                                    previewRequested
                            );
                            browserManager = new BrowserManager(jobHeadless);
                        }

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
                        BrowserCapacityController.shared().marketplaceBackoff(15L * 60_000L);
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

                    } catch (HumanVerificationRequiredException exception) {
                        nextDelayMillis = HumanVerificationRequiredException.RETRY_DELAY_MILLIS;
                        BrowserCapacityController.shared().marketplaceBackoff(nextDelayMillis);
                        delayAllJobs = true;
                        reportQueuedAfterRun = false;
                        telemetryReporter.runFailed(
                                botId,
                                elapsedMillis(startedAtNanos),
                                System.currentTimeMillis() + nextDelayMillis,
                                errorMessage(exception)
                        );
                        log.warn(
                                "[HUMAN VERIFICATION] Bot {} still requires manual CAPTCHA completion after {}. All jobs for this bot wait {} minutes; saved session files are retained. This is not classified as a confirmed session block.",
                                botId,
                                jobType,
                                TimeUnit.MILLISECONDS.toMinutes(nextDelayMillis)
                        );

                    } catch (VintedRateLimitException exception) {
                        BrowserCapacityController.shared().marketplaceBackoff(
                                TimeUnit.SECONDS.toMillis(config.rateLimitDelaySeconds()));
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
                        try {
                            scheduler.completeRun(
                                    botId,
                                    jobType,
                                    nextDelayMillis,
                                    delayAllJobs,
                                    reportQueuedAfterRun
                            );
                        } finally {
                            String closeReason =
                                    "scheduled job finished for bot "
                                            + botId
                                            + " / "
                                            + jobType;

                            browserManager = WorkerBrowserRetentionPolicy.afterJob(
                                    browserManager,
                                    jobHeadless,
                                    runtime -> closeBrowserRuntime(runtime, closeReason)
                            );
                            browserIdleSinceNanos =
                                    browserManager != null && keepBrowserBetweenJobs
                                            ? System.nanoTime() : 0L;
                        }
                    }
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
            closeBrowserRuntime(
                    browserManager,
                    retirementRequested.get()
                            ? "graceful worker slot retirement"
                            : "worker slot shutdown"
            );

            log.info(
                    "[SLOT {}] Worker slot stopped. retired={}",
                    slotNumber,
                    retirementRequested.get()
            );
        }
    }

    private BrowserManager closeBrowserRuntime(
            BrowserManager browserManager,
            String reason
    ) {
        if (browserManager == null) {
            return null;
        }

        log.info(
                "[BROWSER LIFECYCLE] Slot {} is releasing its Playwright browser runtime. reason={}",
                slotNumber,
                reason
        );

        try {
            browserManager.close();
        } catch (Exception exception) {
            log.warn(
                    "[SLOT {}] Could not close Playwright browser runtime cleanly. reason={}, closeError={}",
                    slotNumber,
                    reason,
                    errorMessage(exception)
            );
            log.debug(
                    "[SLOT {}] Full browser-runtime close error.",
                    slotNumber,
                    exception
            );
        }

        /*
         * Never reuse a runtime after close was requested, even if closing
         * reported a problem. BrowserManager marks itself closed before it
         * starts shutting Chromium down, and a later job must launch a fresh
         * runtime instead of touching an uncertain browser process.
         */
        return null;
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

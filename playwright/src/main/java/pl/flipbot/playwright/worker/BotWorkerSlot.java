package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryStateResponse;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.target.VintedRateLimitException;
import pl.flipbot.playwright.target.VintedSessionBlockedException;
import pl.flipbot.playwright.verification.HumanVerificationRequiredException;
import pl.flipbot.playwright.verification.ManualHumanVerificationRecovery;

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
    private final ManualHumanVerificationRecovery manualVerificationRecovery =
            new ManualHumanVerificationRecovery();

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
                "[SLOT {}] Starting worker slot on thread {}. Each claimed job gets a fresh Playwright browser runtime which is closed before the slot waits again; headless={}.",
                slotNumber,
                Thread.currentThread().getName(),
                config.schedulerHeadless()
        );

        try {
            while (!Thread.currentThread().isInterrupted()) {
                ScheduledBotTask task = scheduler.takeNext();
                Long botId = task.botId();
                ScheduledJobType jobType = task.jobType();

                if (jobType == ScheduledJobType.CAPTCHA_RECOVERY) {
                    executeRequestedCaptchaRecovery(botId);
                    continue;
                }

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
                boolean captchaPaused = false;
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

                    BotDetailsDto bot = botApiClient.getBot(botId);

                    try {
                        executeJobInConfiguredBrowser(bot, jobType, false);
                    } catch (HumanVerificationRequiredException challenge) {
                        try {
                            manualVerificationRecovery.recover(bot, challenge);
                        } catch (InterruptedException exception) {
                            throw exception;
                        } catch (VintedRateLimitException exception) {
                            throw exception;
                        } catch (Exception exception) {
                            throw new CaptchaPauseException(
                                    challenge,
                                    exception
                            );
                        }

                        log.info(
                                "[CAPTCHA] Restarting {} for bot {} in the configured headless browser after manual verification. The same refreshed bot-{}.json session will be restored.",
                                jobType,
                                botId,
                                botId
                        );

                        try {
                            executeJobInConfiguredBrowser(bot, jobType, true);
                        } catch (HumanVerificationRequiredException repeatedChallenge) {
                            throw new CaptchaPauseException(
                                    repeatedChallenge,
                                    new IllegalStateException(
                                            "Human verification reappeared immediately after manual completion."
                                    )
                            );
                        }
                    }

                    long durationMs = elapsedMillis(startedAtNanos);
                    telemetryReporter.runSucceeded(botId, durationMs);

                    log.info(
                            "[SLOT {}] Bot {} completed {} in {} ms. Browser runtime is closed; next normal {} interval={} seconds.",
                            slotNumber,
                            botId,
                            jobType,
                            durationMs,
                            jobType,
                            config.normalDelaySeconds(jobType)
                    );

                } catch (CaptchaPauseException exception) {
                    captchaPaused = true;
                    persistCaptchaPause(
                            botId,
                            elapsedMillis(startedAtNanos),
                            exception.challenge(),
                            exception.reason()
                    );

                    log.warn(
                            "[CAPTCHA] Bot {} is paused after the initial visible window was not completed. No scheduled job or browser will retry automatically. Use 'Zaakceptuj CAPTCHA' in Runtime when someone is at the computer.",
                            botId
                    );
                    log.debug(
                            "[CAPTCHA] Full pause reason for bot {} during {}.",
                            botId,
                            jobType,
                            exception
                    );

                } catch (InterruptedException exception) {
                    throw exception;

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
                    if (!captchaPaused) {
                        scheduler.completeRun(
                                botId,
                                jobType,
                                nextDelayMillis,
                                delayAllJobs,
                                reportQueuedAfterRun
                        );
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
            log.info("[SLOT {}] Worker slot stopped.", slotNumber);
        }
    }

    private void executeRequestedCaptchaRecovery(Long botId)
            throws InterruptedException {
        long startedAtNanos = System.nanoTime();
        String challengeUrl = null;

        try {
            RuntimeTelemetryStateResponse state =
                    telemetryReporter.currentState(botId);
            challengeUrl = state == null
                    ? null
                    : state.captchaChallengeUrl();

            telemetryReporter.captchaRecoveryStarted(botId, slotNumber);

            BotDetailsDto bot = botApiClient.getBot(botId);
            HumanVerificationRequiredException challenge =
                    new HumanVerificationRequiredException(
                            challengeUrl,
                            "manual recovery requested from Runtime"
                    );

            log.warn(
                    "[CAPTCHA] Runtime recovery requested for bot {}. Opening the single visible recovery window with the same bot-{}.json session.",
                    botId,
                    botId
            );

            manualVerificationRecovery.recover(bot, challenge);

            long durationMs = elapsedMillis(startedAtNanos);
            telemetryReporter.captchaRecoverySucceeded(botId, durationMs);
            scheduler.completeCaptchaRecovery(botId, true);

            log.info(
                    "[CAPTCHA] Bot {} passed manual verification in {} ms. Normal scheduled jobs are enabled again.",
                    botId,
                    durationMs
            );
        } catch (InterruptedException exception) {
            scheduler.completeCaptchaRecovery(botId, false);
            throw exception;
        } catch (VintedSessionBlockedException exception) {
            try {
                RuntimeTelemetryReporter.SessionBlockCooldown cooldown =
                        telemetryReporter.sessionBlocked(
                                botId,
                                elapsedMillis(startedAtNanos),
                                errorMessage(exception)
                        );
                scheduler.completeCaptchaRecovery(botId, true);

                log.warn(
                        "[SESSION BLOCK] Manual CAPTCHA recovery for bot {} reached a Vinted session block instead. CAPTCHA pause was cleared and all normal jobs will respect cooldown attempt #{} until {}.",
                        botId,
                        cooldown.attemptNumber(),
                        cooldown.nextRunAtEpochMs()
                );
            } catch (Exception telemetryException) {
                HumanVerificationRequiredException challenge =
                        new HumanVerificationRequiredException(
                                challengeUrl,
                                "manual recovery reached a session block"
                        );
                persistCaptchaPause(
                        botId,
                        elapsedMillis(startedAtNanos),
                        challenge,
                        telemetryException
                );
            }
        } catch (Exception exception) {
            HumanVerificationRequiredException challenge =
                    new HumanVerificationRequiredException(
                            challengeUrl,
                            "manual recovery requested from Runtime"
                    );

            persistCaptchaPause(
                    botId,
                    elapsedMillis(startedAtNanos),
                    challenge,
                    exception
            );

            log.warn(
                    "[CAPTCHA] Manual recovery for bot {} was not completed. The visible browser is closed and the bot remains paused until the Runtime button is clicked again. reason={}",
                    botId,
                    errorMessage(exception)
            );
            log.debug(
                    "[CAPTCHA] Full requested-recovery failure for bot {}.",
                    botId,
                    exception
            );
        }
    }

    private void persistCaptchaPause(
            Long botId,
            long durationMs,
            HumanVerificationRequiredException challenge,
            Exception reason
    ) {
        try {
            telemetryReporter.captchaRequired(
                    botId,
                    durationMs,
                    errorMessage(reason),
                    challenge.challengeUrl()
            );
        } catch (Exception telemetryException) {
            log.error(
                    "[CAPTCHA] Could not persist CAPTCHA_REQUIRED for bot {}. The in-memory scheduler still pauses it for this process. reason={}",
                    botId,
                    errorMessage(telemetryException)
            );
        }

        scheduler.pauseForCaptcha(botId);
    }

    private void executeJobInConfiguredBrowser(
            BotDetailsDto bot,
            ScheduledJobType jobType,
            boolean postVerificationRetry
    ) {
        log.info(
                "[BROWSER LIFECYCLE] Slot {} launching a fresh Playwright browser for bot {} / {}. "
                        + "The bot-specific stored session will be restored by BotContext and the browser will be closed when this job finishes. headless={}, postVerificationRetry={}.",
                slotNumber,
                bot.getId(),
                jobType,
                config.schedulerHeadless(),
                postVerificationRetry
        );

        try (BrowserManager browserManager =
                     new BrowserManager(config.schedulerHeadless())) {
            ScheduledBotRunExecutor runExecutor =
                    new ScheduledBotRunExecutor(bot, browserManager);

            runExecutor.executeJob(jobType);
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

    private static final class CaptchaPauseException extends RuntimeException {

        private final HumanVerificationRequiredException challenge;
        private final Exception reason;

        private CaptchaPauseException(
                HumanVerificationRequiredException challenge,
                Exception cause
        ) {
            super(cause);
            this.challenge = challenge;
            this.reason = cause;
        }

        private HumanVerificationRequiredException challenge() {
            return challenge;
        }

        private Exception reason() {
            return reason;
        }
    }
}

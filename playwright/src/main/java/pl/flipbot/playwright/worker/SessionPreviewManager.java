package pl.flipbot.playwright.worker;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.BotApiClient;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryReporter;
import pl.flipbot.playwright.api.runtime.RuntimeTelemetryStateResponse;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.marketplace.MarketplaceUrls;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.session.VintedSessionPersistenceGuard;
import pl.flipbot.playwright.target.VintedRateLimitException;
import pl.flipbot.playwright.target.VintedSessionBlockedException;
import pl.flipbot.playwright.verification.HumanVerificationHandler;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Gives one bot an exclusive, visible Playwright owner while session preview is
 * enabled.
 *
 * <p>The bot is removed from generic worker claiming, but it is NOT paused as
 * business logic: this preview owner claims the same scheduler jobs and runs
 * the normal {@link ScheduledBotRunExecutor} in headed Chromium. The user can
 * therefore watch catalog scans and negotiation checks exactly as they happen.</p>
 *
 * <p>Between scheduled jobs a normal persistent BotContext stays visible. If
 * Vinted presents CAPTCHA/authentication trouble, the user may solve it in that
 * window. The context is persisted only after a strong authenticated signal is
 * visible, using the normal SessionManager staging/validation/backup path. No
 * second browser context for this bot runs concurrently.</p>
 */
@Slf4j
final class SessionPreviewManager implements AutoCloseable {

    private static final long PREVIEW_POLL_MS = 250L;
    private static final long STOP_TIMEOUT_SECONDS = 8L;
    private static final long SESSION_BLOCK_FALLBACK_DELAY_MINUTES = 15L;
    private static final int LIVE_PREVIEW_TELEMETRY_SLOT = 0;

    private final WorkerRuntimeConfig config;
    private final RuntimeTelemetryReporter telemetryReporter;
    private final BotApiClient botApiClient = new BotApiClient();

    private final ExecutorService executor =
            Executors.newCachedThreadPool(
                    namedThreadFactory("flipbot-live-preview-")
            );

    private final Map<Long, PreviewHandle> handles =
            new ConcurrentHashMap<>();

    SessionPreviewManager(
            WorkerRuntimeConfig config,
            RuntimeTelemetryReporter telemetryReporter
    ) {
        this.config = config;
        this.telemetryReporter = telemetryReporter;
    }

    void stopUnrequested(Set<Long> requestedBotIds) {
        Set<Long> requested = normalize(requestedBotIds);

        for (Map.Entry<Long, PreviewHandle> entry
                : Set.copyOf(handles.entrySet())) {

            Long botId = entry.getKey();
            if (requested.contains(botId)) {
                continue;
            }

            stopAndAwait(botId, entry.getValue());
        }
    }

    void startRequestedWhenSafe(
            Set<Long> requestedBotIds,
            BotRunScheduler scheduler
    ) {
        Set<Long> requested = normalize(requestedBotIds);

        for (Long botId : requested) {
            if (handles.containsKey(botId)) {
                continue;
            }

            if (scheduler.isWorking(botId)) {
                log.info(
                        "[SESSION PREVIEW] Bot {} live preview requested while a normal job is already WORKING. That job will finish normally; live headed ownership starts immediately afterwards.",
                        botId
                );
                continue;
            }

            start(botId, scheduler);
        }
    }

    Set<Long> activeBotIds() {
        Set<Long> active = new HashSet<>();

        handles.forEach(
                (botId, handle) -> {
                    Future<?> future = handle.future;
                    if (future != null && !future.isDone()) {
                        active.add(botId);
                    }
                }
        );

        return Set.copyOf(active);
    }

    private void start(
            Long botId,
            BotRunScheduler scheduler
    ) {
        PreviewHandle handle = new PreviewHandle();

        if (handles.putIfAbsent(botId, handle) != null) {
            return;
        }

        try {
            handle.future = executor.submit(
                    () -> runLivePreview(botId, handle, scheduler)
            );

            log.info(
                    "[SESSION PREVIEW] Starting LIVE headed owner for bot {}. The bot keeps its normal scheduler jobs while this window is visible.",
                    botId
            );
        } catch (RuntimeException exception) {
            handles.remove(botId, handle);
            throw exception;
        }
    }

    private void runLivePreview(
            Long botId,
            PreviewHandle handle,
            BotRunScheduler scheduler
    ) {
        BotContext idleContext = null;

        try {
            BotDetailsDto bot = botApiClient.getBot(botId);

            try (BrowserManager browserManager = new BrowserManager(false)) {
                RecoveryMonitor recoveryMonitor = new RecoveryMonitor();

                idleContext = openIdleContext(
                        bot,
                        browserManager,
                        recoveryMonitor
                );

                while (!handle.stopRequested.get()) {
                    ScheduledBotTask task = scheduler.pollPreviewNext(
                            botId,
                            PREVIEW_POLL_MS
                    );

                    if (task == null) {
                        observeManualRecovery(
                                idleContext,
                                recoveryMonitor,
                                scheduler
                        );
                        continue;
                    }

                    persistHealthyIdleState(
                            idleContext,
                            "before normal scheduled job"
                    );
                    closeContext(idleContext, botId, "before scheduled job");
                    idleContext = null;

                    executeScheduledTask(
                            botId,
                            task,
                            bot,
                            browserManager,
                            scheduler
                    );

                    if (!handle.stopRequested.get()) {
                        recoveryMonitor = new RecoveryMonitor();
                        idleContext = openIdleContext(
                                bot,
                                browserManager,
                                recoveryMonitor
                        );
                    }
                }

                persistHealthyIdleState(
                        idleContext,
                        "live preview close"
                );
                closeContext(
                        idleContext,
                        botId,
                        "live preview close"
                );
                idleContext = null;
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.info(
                    "[SESSION PREVIEW] Live preview owner for bot {} was interrupted.",
                    botId
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[SESSION PREVIEW] Live preview owner for bot {} failed. Generic scheduler ownership remains blocked while preview is requested; the next sync may restart the live preview safely. reason={}",
                    botId,
                    errorMessage(exception),
                    exception
            );
        } finally {
            closeContext(idleContext, botId, "live preview owner finished");
            handle.finished.set(true);
        }
    }

    private BotContext openIdleContext(
            BotDetailsDto bot,
            BrowserManager browserManager,
            RecoveryMonitor recoveryMonitor
    ) {
        BotContext context = new BotContext(bot, browserManager);
        Page page = context.getPage();

        try {
            page.navigate(
                    MarketplaceUrls.HOME,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(30_000)
            );
        } catch (RuntimeException exception) {
            /*
             * A challenge page or slow Vinted response is exactly when the
             * visible session is useful. Keep the browser open instead of
             * turning preview navigation into another failed bot job.
             */
            log.warn(
                    "[SESSION PREVIEW] Initial visible navigation for bot {} did not settle cleanly. Keeping the window open for manual inspection/recovery. url={}, reason={}",
                    bot.getId(),
                    safePageUrl(page),
                    errorMessage(exception)
            );
        }

        recoveryMonitor.recoveryObserved = !isHealthyAuthenticated(context)
                || isVerificationVisible(page)
                || hasPersistedSessionBlock(bot.getId());

        log.info(
                "[SESSION PREVIEW] LIVE window READY for bot {}. url={}, storedSessionRestored={}, recoveryObserved={}. Normal bot jobs continue in this headed Chromium owner.",
                bot.getId(),
                safePageUrl(page),
                context.isStoredSessionRestored(),
                recoveryMonitor.recoveryObserved
        );

        return context;
    }

    private void observeManualRecovery(
            BotContext context,
            RecoveryMonitor monitor,
            BotRunScheduler scheduler
    ) {
        if (context == null
                || context.getPage() == null
                || context.getPage().isClosed()) {
            return;
        }

        Page page = context.getPage();
        boolean verificationVisible = isVerificationVisible(page);
        boolean healthy = isHealthyAuthenticated(context);

        if (verificationVisible || !healthy) {
            monitor.recoveryObserved = true;
            return;
        }

        if (!monitor.recoveryObserved) {
            return;
        }

        try {
            context.saveSession();
            telemetryReporter.sessionRecovered(
                    context.getBot().getId()
            );
            scheduler.resumeAfterSessionRecovery(
                    context.getBot().getId()
            );
            monitor.recoveryObserved = false;

            log.warn(
                    "[SESSION PREVIEW] Manual session recovery CONFIRMED for bot {}. Strong authenticated Vinted evidence is visible after the previous challenge/auth problem. Fresh storage state was validated and installed through the normal session backup path.",
                    context.getBot().getId()
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "[SESSION PREVIEW] Bot {} became visibly authenticated after manual recovery, but the refreshed storage state was not safe to install. Existing production session remains untouched. reason={}",
                    context.getBot().getId(),
                    errorMessage(exception)
            );
        }
    }

    private void persistHealthyIdleState(
            BotContext context,
            String reason
    ) {
        if (context == null || !isHealthyAuthenticated(context)) {
            return;
        }

        try {
            context.saveSession();
            log.debug(
                    "[SESSION PREVIEW] Persisted healthy visible session checkpoint for bot {}. reason={}",
                    context.getBot().getId(),
                    reason
            );
        } catch (RuntimeException exception) {
            log.warn(
                    "[SESSION PREVIEW] Could not persist healthy visible session checkpoint for bot {}. Existing session remains available. reason={}, error={}",
                    context.getBot().getId(),
                    reason,
                    errorMessage(exception)
            );
        }
    }

    private boolean isHealthyAuthenticated(BotContext context) {
        if (context == null) {
            return false;
        }

        try {
            return new VintedSessionPersistenceGuard()
                    .check(context)
                    .healthy();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean isVerificationVisible(Page page) {
        if (page == null || page.isClosed()) {
            return false;
        }

        try {
            return new HumanVerificationHandler()
                    .isHumanVerificationVisible(page);
        } catch (VintedSessionBlockedException exception) {
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void executeScheduledTask(
            Long botId,
            ScheduledBotTask task,
            BotDetailsDto bot,
            BrowserManager browserManager,
            BotRunScheduler scheduler
    ) {
        ScheduledJobType jobType = task.jobType();

        Long persistedBlockDelayMillis =
                persistedSessionBlockDelay(botId);

        if (persistedBlockDelayMillis != null
                && persistedBlockDelayMillis > 0L) {
            log.warn(
                    "[SESSION PREVIEW] Bot {} remains under persisted Vinted session cooldown for about {} minute(s). The visible window stays available for manual recovery, but scheduled marketplace actions will not run early.",
                    botId,
                    Math.max(
                            1L,
                            TimeUnit.MILLISECONDS.toMinutes(
                                    persistedBlockDelayMillis
                            )
                    )
            );

            scheduler.completeRun(
                    botId,
                    jobType,
                    persistedBlockDelayMillis,
                    true,
                    false
            );
            return;
        }

        long nextDelayMillis = TimeUnit.SECONDS.toMillis(
                config.normalDelaySeconds(jobType)
        );
        boolean delayAllJobs = false;
        boolean reportQueuedAfterRun = true;
        long startedAtNanos = System.nanoTime();

        telemetryReporter.runStarted(
                botId,
                LIVE_PREVIEW_TELEMETRY_SLOT
        );

        try {
            log.info(
                    "[SESSION PREVIEW] Running normal {} for bot {} in LIVE headed browser.",
                    jobType,
                    botId
            );

            new ScheduledBotRunExecutor(
                    bot,
                    browserManager
            ).executeJob(jobType);

            long durationMs = elapsedMillis(startedAtNanos);
            telemetryReporter.runSucceeded(botId, durationMs);

            log.info(
                    "[SESSION PREVIEW] Bot {} completed normal {} in {} ms while live preview remained enabled.",
                    botId,
                    jobType,
                    durationMs
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
                        cooldown.nextRunAtEpochMs()
                                - System.currentTimeMillis()
                );
            } catch (Exception telemetryException) {
                nextDelayMillis = TimeUnit.MINUTES.toMillis(
                        SESSION_BLOCK_FALLBACK_DELAY_MINUTES
                );
            }

            log.warn(
                    "[SESSION PREVIEW] Vinted session block detected for bot {} during {} (attempt {}). Normal scheduled actions back off for about {} minute(s), but the headed window stays available so the user can complete CAPTCHA/authentication manually.",
                    botId,
                    jobType,
                    attemptNumber,
                    Math.max(
                            1L,
                            TimeUnit.MILLISECONDS.toMinutes(nextDelayMillis)
                    )
            );

        } catch (VintedRateLimitException exception) {
            nextDelayMillis = TimeUnit.SECONDS.toMillis(
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
                    "[SESSION PREVIEW] Bot {} hit a Vinted rate limit during {}. Live window remains visible, but all normal jobs respect the {}s cooldown.",
                    botId,
                    jobType,
                    config.rateLimitDelaySeconds()
            );

        } catch (Exception exception) {
            nextDelayMillis = TimeUnit.SECONDS.toMillis(
                    config.failureDelaySeconds()
            );
            delayAllJobs = false;
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
                    "[SESSION PREVIEW] Bot {} failed during normal {} in live headed mode. Only that job type retries in {}s; visible session stays available. reason={}",
                    botId,
                    jobType,
                    config.failureDelaySeconds(),
                    errorMessage(exception)
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

    private boolean hasPersistedSessionBlock(Long botId) {
        try {
            RuntimeTelemetryStateResponse state =
                    telemetryReporter.currentState(botId);

            return state != null
                    && state.sessionBlockedSince() != null;
        } catch (Exception exception) {
            log.debug(
                    "[SESSION PREVIEW] Could not read persisted session-block marker for bot {} while opening live preview. The visible page state remains authoritative for recovery detection.",
                    botId
            );
            return false;
        }
    }

    private Long persistedSessionBlockDelay(Long botId) {
        try {
            RuntimeTelemetryStateResponse state =
                    telemetryReporter.currentState(botId);

            if (state == null
                    || state.sessionBlockedSince() == null
                    || state.nextRunAt() == null) {
                return null;
            }

            long remaining = Instant.parse(
                    state.nextRunAt()
            ).toEpochMilli() - System.currentTimeMillis();

            return Math.max(0L, remaining);
        } catch (Exception exception) {
            log.warn(
                    "[SESSION PREVIEW] Could not read persisted session cooldown for bot {}. In-memory schedule remains authoritative for this process. reason={}",
                    botId,
                    errorMessage(exception)
            );
            return null;
        }
    }

    private void closeContext(
            BotContext context,
            Long botId,
            String reason
    ) {
        if (context == null) {
            return;
        }

        try {
            context.close();
        } catch (Exception exception) {
            log.warn(
                    "[SESSION PREVIEW] Could not close visible context for bot {} cleanly. reason={}, error={}",
                    botId,
                    reason,
                    errorMessage(exception)
            );
        }
    }

    private void stopAndAwait(
            Long botId,
            PreviewHandle handle
    ) {
        handle.stopRequested.set(true);

        Future<?> future = handle.future;

        if (future == null) {
            handles.remove(botId, handle);
            return;
        }

        try {
            future.get(
                    STOP_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            );
            handles.remove(botId, handle);

            log.info(
                    "[SESSION PREVIEW] LIVE preview for bot {} is fully closed. Generic worker ownership may resume.",
                    botId
            );
        } catch (TimeoutException exception) {
            log.error(
                    "[SESSION PREVIEW] Timed out waiting for bot {} live preview to close. Keeping this bot scheduler-owned by preview until its thread actually exits, preventing two browsers from racing the same session.",
                    botId
            );
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            handles.remove(botId, handle);
            log.warn(
                    "[SESSION PREVIEW] Live preview thread for bot {} finished with an error while closing.",
                    botId,
                    exception
            );
        }
    }

    private Set<Long> normalize(Set<Long> botIds) {
        if (botIds == null || botIds.isEmpty()) {
            return Set.of();
        }

        Set<Long> normalized = new HashSet<>();

        botIds.stream()
                .filter(botId -> botId != null && botId > 0L)
                .forEach(normalized::add);

        return Set.copyOf(normalized);
    }

    @Override
    public void close() {
        Set<Long> botIds = Set.copyOf(handles.keySet());

        for (Long botId : botIds) {
            PreviewHandle handle = handles.get(botId);
            if (handle != null) {
                stopAndAwait(botId, handle);
            }
        }

        executor.shutdownNow();

        try {
            if (!executor.awaitTermination(
                    STOP_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
            )) {
                log.warn(
                        "[SESSION PREVIEW] Live preview executor did not terminate within {} seconds.",
                        STOP_TIMEOUT_SECONDS
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(
                0L,
                (System.nanoTime() - startedAtNanos) / 1_000_000L
        );
    }

    private String safePageUrl(Page page) {
        if (page == null) {
            return "<no-page>";
        }

        try {
            return page.url();
        } catch (RuntimeException exception) {
            return "<unavailable>";
        }
    }

    private String errorMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }

        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        String firstLine = message.lines()
                .findFirst()
                .orElse(message)
                .trim();

        return throwable.getClass().getSimpleName()
                + ": "
                + firstLine;
    }

    private static java.util.concurrent.ThreadFactory namedThreadFactory(
            String prefix
    ) {
        AtomicInteger sequence = new AtomicInteger(1);

        return runnable -> {
            Thread thread = new Thread(
                    runnable,
                    prefix + sequence.getAndIncrement()
            );
            thread.setDaemon(false);
            return thread;
        };
    }

    private static final class RecoveryMonitor {
        private boolean recoveryObserved;
    }

    private static final class PreviewHandle {
        private final AtomicBoolean stopRequested =
                new AtomicBoolean(false);
        private final AtomicBoolean finished =
                new AtomicBoolean(false);
        private volatile Future<?> future;
    }
}

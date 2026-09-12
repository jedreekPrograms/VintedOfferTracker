package pl.flipbot.playwright.worker;

import com.microsoft.playwright.Page;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.login.LoginService;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.negotiation.ExistingNegotiationProcessor;
import pl.flipbot.playwright.processing.CatalogWorkProcessor;
import pl.flipbot.playwright.probe.PriceProbeProcessor;
import pl.flipbot.playwright.probe.PriceProbeRuntimeConfig;
import pl.flipbot.playwright.probe.SandboxCloneLoginService;
import pl.flipbot.playwright.session.SessionManager;
import pl.flipbot.playwright.session.VintedSessionPersistenceGuard;
import pl.flipbot.playwright.target.VintedSessionBlockDetector;
import pl.flipbot.playwright.target.VintedSessionBlockedException;
import pl.flipbot.playwright.target.VintedSessionFailureClassifier;

@Slf4j
public class ScheduledBotRunExecutor {

    private static final ScheduledRealActionConfig REAL_ACTION_CONFIG =
            ScheduledRealActionConfig.fromEnvironment();
    private static final ScheduledActionLimitConfig ACTION_LIMIT_CONFIG =
            ScheduledActionLimitConfig.fromEnvironment();
    private static final PriceProbeRuntimeConfig PRICE_PROBE_CONFIG =
            PriceProbeRuntimeConfig.fromEnvironment();

    private static final long SESSION_BLOCK_CLASSIFICATION_WINDOW_MS = 2_000L;
    private static final long SESSION_BLOCK_CLASSIFICATION_POLL_MS = 200L;

    private final BotDetailsDto bot;
    private final BrowserManager browserManager;
    private final VintedSessionBlockDetector sessionBlockDetector =
            new VintedSessionBlockDetector();
    private final VintedSessionPersistenceGuard sessionPersistenceGuard =
            new VintedSessionPersistenceGuard();

    public ScheduledBotRunExecutor(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {
        if (bot == null || bot.getId() == null || bot.getId() <= 0) {
            throw new IllegalArgumentException(
                    "Bot details with a positive ID are required."
            );
        }

        this.bot = bot;
        this.browserManager = browserManager;
    }

    public void executeOneRun() {
        executeInternal(null);
    }

    public void executeJob(ScheduledJobType jobType) {
        if (jobType == null) {
            throw new IllegalArgumentException("Scheduled job type is required.");
        }
        executeInternal(jobType);
    }

    private void executeInternal(ScheduledJobType jobType) {
        Long botId = bot.getId();
        BotContext context = new BotContext(bot, browserManager);
        boolean loginReady = false;
        boolean jobCompleted = false;
        boolean authenticatedCheckpointReady = false;

        try {
            if (jobType == ScheduledJobType.PRICE_PROBE) {
                if (!PRICE_PROBE_CONFIG.enabled()) {
                    log.info(
                            "[PRICE PROBE] Job for bot {} skipped because FLIPBOT_PRICE_PROBE_ENABLED=false.",
                            botId
                    );
                    return;
                }

                new SandboxCloneLoginService(context, PRICE_PROBE_CONFIG).login();
                loginReady = true;
                new PriceProbeProcessor(context, PRICE_PROBE_CONFIG).processOne();
                jobCompleted = true;
                return;
            }

            boolean firstOfferRequested =
                    jobType == ScheduledJobType.CATALOG_SCAN
                            && REAL_ACTION_CONFIG.realOffersRequestedFor(botId);
            boolean nextStepRequested =
                    jobType == ScheduledJobType.NEGOTIATION_CHECK
                            && REAL_ACTION_CONFIG.realNextStepsRequestedFor(botId);
            boolean realOffersEnabled =
                    jobType == ScheduledJobType.CATALOG_SCAN
                            && REAL_ACTION_CONFIG.realOffersEnabledFor(botId);
            boolean realNextStepsEnabled =
                    jobType == ScheduledJobType.NEGOTIATION_CHECK
                            && REAL_ACTION_CONFIG.realNextStepsEnabledFor(botId);
            boolean productionModeEnabled = REAL_ACTION_CONFIG.productionModeEnabled();

            int maxRealOffersPerRun = ACTION_LIMIT_CONFIG.effectiveMaxRealOffers(
                    productionModeEnabled
            );
            int maxRealNextStepsPerRun = ACTION_LIMIT_CONFIG.effectiveMaxRealNextSteps(
                    productionModeEnabled
            );

            LoginService loginService = new LoginService(context);
            ListingClient listingClient = new ListingClient();
            OfferQuotaClient offerQuotaClient = new OfferQuotaClient();

            if (firstOfferRequested || nextStepRequested) {
                RealActionPreflight.Result preflight = new RealActionPreflight().validate(
                        bot,
                        jobType,
                        listingClient,
                        firstOfferRequested,
                        nextStepRequested
                );

                if (!preflight.ready()) {
                    realOffersEnabled = false;
                    realNextStepsEnabled = false;

                    if (preflight.expectedCapacityBlock()) {
                        log.info(
                                "[REAL ACTION PREFLIGHT] Bot {} / {} has no room for a new full negotiation right now. Real FIRST_OFFER submit is disabled for this cycle, but catalog discovery and historical-backlog processing continue safely.",
                                botId,
                                jobType
                        );
                    } else {
                        log.error(
                                "[REAL ACTION PREFLIGHT] Real actions downgraded to DRY RUN for bot {} / {} because preflight found a real configuration/runtime failure.",
                                botId,
                                jobType
                        );
                    }
                }
            }

            if (REAL_ACTION_CONFIG.preflightOnly()
                    && (firstOfferRequested || nextStepRequested)) {
                realOffersEnabled = false;
                realNextStepsEnabled = false;
                log.warn(
                        "[REAL ACTION PREFLIGHT] PREFLIGHT ONLY is active for bot {} / {}. Validation may report READY, but real submit remains disabled.",
                        botId,
                        jobType
                );
            }

            ListingStatusUpdater listingStatusUpdater = new ListingStatusUpdater(
                    context,
                    listingClient
            );

            ExistingNegotiationProcessor existingNegotiationProcessor =
                    new ExistingNegotiationProcessor(
                            context,
                            listingClient,
                            offerQuotaClient,
                            listingStatusUpdater,
                            realNextStepsEnabled,
                            maxRealNextStepsPerRun
                    );

            CatalogWorkProcessor catalogWorkProcessor = new CatalogWorkProcessor(
                    context,
                    listingClient,
                    offerQuotaClient,
                    listingStatusUpdater,
                    realOffersEnabled,
                    maxRealOffersPerRun
            );

            BotRunExecutor botRunExecutor = new BotRunExecutor(
                    context,
                    existingNegotiationProcessor,
                    catalogWorkProcessor,
                    realOffersEnabled,
                    REAL_ACTION_CONFIG.firstOfferOneShotTestModeEnabled()
            );

            logExecutionMode(
                    jobType,
                    botId,
                    realOffersEnabled,
                    realNextStepsEnabled,
                    firstOfferRequested,
                    nextStepRequested,
                    maxRealOffersPerRun,
                    maxRealNextStepsPerRun
            );

            loginService.login();
            loginReady = true;

            /*
             * Capture a checkpoint immediately after LoginService has positively
             * verified authentication. If Vinted sends the job through
             * /session-refresh or login UI later, this becomes the safe rollback
             * point instead of persisting the degraded end-of-job browser state.
             *
             * LoginService may already have saved an interactive login. Saving
             * once more here intentionally rotates that freshly authenticated
             * state into last-known-good as well.
             */
            context.saveSession();
            authenticatedCheckpointReady = true;
            log.debug(
                    "[SESSION] Captured authenticated pre-job checkpoint for bot {}.",
                    botId
            );

            if (jobType == null) {
                botRunExecutor.executeOneRun();
            } else {
                switch (jobType) {
                    case NEGOTIATION_CHECK -> botRunExecutor.executeNegotiationCheck();
                    case CATALOG_SCAN -> botRunExecutor.executeCatalogScan();
                    case PRICE_PROBE -> throw new IllegalStateException(
                            "PRICE_PROBE must use the isolated probe execution path."
                    );
                }
            }

            jobCompleted = true;
        } catch (VintedSessionBlockedException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            /*
             * Last-resort classification for every Vinted job. Login/auth UI can
             * fail first and the dedicated hard-block page can finish rendering a
             * fraction of a second later. Poll briefly before turning the run into
             * a generic failure so an actual "Twoja sesja została zablokowana"
             * page always reaches the scheduler as VintedSessionBlockedException.
             */
            classifyLateSessionBlock(
                    context,
                    jobType,
                    botId
            );

            /*
             * Vinted can also leave the credential form visible after all three
             * deterministic submit mechanisms without rendering the hard-block
             * page soon enough to read its text. Retrying that state every minute
             * is exactly the hammering the session cooldown is meant to prevent.
             * Only the narrow, known login-stall signatures are upgraded here;
             * explicit credential failures and unrelated errors stay generic.
             */
            if (VintedSessionFailureClassifier.shouldUseProtectiveCooldown(exception)) {
                String jobLabel = jobType == null ? "FULL_RUN" : jobType.name();

                /*
                 * If the active file had previously been saved from an unhealthy
                 * session-refresh page, move it to the recovery slot and restore
                 * the previous authenticated rotating checkpoint before the bot
                 * enters cooldown. No clean browser state is fabricated.
                 */
                restoreLastKnownGoodSession(botId, "authentication stall");

                log.warn(
                        "[SESSION BLOCK] Bot {} hit a repeated Vinted authentication stall during {}. Treating it as a protective session cooldown instead of RUN_FAILED so all bot jobs back off together.",
                        botId,
                        jobLabel
                );
                throw new VintedSessionBlockedException(
                        "Vinted authentication stalled after repeated submit/verification attempts while running "
                                + jobLabel
                                + " for bot "
                                + botId
                                + ". Applying protective session cooldown. Original failure: "
                                + friendlyMessage(exception)
                );
            }

            throw exception;
        } finally {
            if (loginReady && jobCompleted) {
                if (authenticatedCheckpointReady) {
                    VintedSessionPersistenceGuard.Check check =
                            sessionPersistenceGuard.check(context);

                    if (check.healthy()) {
                        try {
                            context.saveSession();
                        } catch (Exception exception) {
                            log.warn(
                                    "[SCHEDULED JOB] Could not save session for bot {} after a successful, authenticated job; the pre-job checkpoint remains protected.",
                                    botId,
                                    exception
                            );
                        }
                    } else {
                        log.warn(
                                "[SESSION] Bot {} job returned normally but its final Vinted state is not safe to persist. Refusing to replace bot-{}.json and restoring the authenticated pre-job checkpoint. reason={}",
                                botId,
                                botId,
                                check.reason()
                        );
                        restoreLastKnownGoodSession(
                                botId,
                                "unhealthy end-of-job authentication state"
                        );
                    }
                } else {
                    /* PRICE_PROBE keeps its existing isolated persistence path. */
                    try {
                        context.saveSession();
                    } catch (Exception exception) {
                        log.warn(
                                "[SCHEDULED JOB] Could not save session for bot {} after a successful job; the previous active session remains protected.",
                                botId,
                                exception
                        );
                    }
                }
            } else if (loginReady) {
                log.warn(
                        "[SESSION] Bot {} job did not complete successfully. Refusing to persist the current browser state so a transient logout/error cannot replace the last-known-good session.",
                        botId
                );
            }

            log.info(
                    "[BROWSER LIFECYCLE] Bot {} {} job is finished. Closing only this job's isolated browser context/page.",
                    botId,
                    jobType == null ? "FULL_RUN" : jobType
            );

            try {
                context.close();
            } catch (Exception exception) {
                log.warn(
                        "[SCHEDULED JOB] Could not close browser context cleanly for bot {}.",
                        botId,
                        exception
                );
            }
        }
    }

    private void restoreLastKnownGoodSession(
            Long botId,
            String reason
    ) {
        try {
            boolean restored = new SessionManager().restoreLastKnownGood(botId);
            if (!restored) {
                log.warn(
                        "[SESSION] Bot {} could not roll back after {} because no last-known-good snapshot exists yet.",
                        botId,
                        reason
                );
            }
        } catch (RuntimeException restoreFailure) {
            log.error(
                    "[SESSION] Failed to restore last-known-good state for bot {} after {}. Active session was not intentionally deleted.",
                    botId,
                    reason,
                    restoreFailure
            );
        }
    }

    private void classifyLateSessionBlock(
            BotContext context,
            ScheduledJobType jobType,
            Long botId
    ) {
        Page page = context.getPage();
        if (page == null || page.isClosed()) {
            return;
        }

        String jobLabel = jobType == null ? "FULL_RUN" : jobType.name();
        long deadline = System.currentTimeMillis() + SESSION_BLOCK_CLASSIFICATION_WINDOW_MS;

        while (true) {
            sessionBlockDetector.throwIfBlocked(
                    page,
                    "classifying failed " + jobLabel + " for bot " + botId
            );

            if (System.currentTimeMillis() >= deadline
                    || page.isClosed()) {
                return;
            }

            page.waitForTimeout(SESSION_BLOCK_CLASSIFICATION_POLL_MS);
        }
    }

    private String friendlyMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }

        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return message.lines()
                .findFirst()
                .orElse(message)
                .trim();
    }

    private void logExecutionMode(
            ScheduledJobType jobType,
            Long botId,
            boolean realOffersEnabled,
            boolean realNextStepsEnabled,
            boolean firstOfferRequested,
            boolean nextStepRequested,
            int maxRealOffersPerRun,
            int maxRealNextStepsPerRun
    ) {
        String jobLabel = jobType == null ? "FULL_RUN" : jobType.name();

        if (REAL_ACTION_CONFIG.preflightOnly()
                && (firstOfferRequested || nextStepRequested)) {
            log.warn(
                    "[SCHEDULED JOB] Preparing {} for bot {} in PREFLIGHT ONLY / DRY RUN mode. requestedFirstOffer={}, requestedNextStep={}, realOffers=false, realNextSteps=false.",
                    jobLabel,
                    botId,
                    firstOfferRequested,
                    nextStepRequested
            );
            return;
        }

        if (!realOffersEnabled && !realNextStepsEnabled) {
            log.info(
                    "[SCHEDULED JOB] Preparing {} for bot {} in DRY RUN / NO REAL SUBMIT mode.",
                    jobLabel,
                    botId
            );
            return;
        }

        String modeLabel = REAL_ACTION_CONFIG.productionModeEnabled()
                ? "PRODUCTION REAL ACTION MODE"
                : "CONTROLLED REAL ACTION MODE";

        log.info(
                "[SCHEDULED JOB] {} for {} / bot {}. realOffers={}, realNextSteps={}, firstOfferOneShotTestMode={}, maxRealOffersPerRun={}, maxRealNextStepsPerRun={}.",
                modeLabel,
                jobLabel,
                botId,
                realOffersEnabled,
                realNextStepsEnabled,
                REAL_ACTION_CONFIG.firstOfferOneShotTestModeEnabled(),
                maxRealOffersPerRun,
                maxRealNextStepsPerRun
        );
    }
}

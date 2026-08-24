package pl.flipbot.playwright.worker;

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
import pl.flipbot.playwright.probe.PriceProbeTestStealthProfile;
import pl.flipbot.playwright.probe.SandboxCloneLoginService;

@Slf4j
public class ScheduledBotRunExecutor {

    private static final ScheduledRealActionConfig REAL_ACTION_CONFIG =
            ScheduledRealActionConfig.fromEnvironment();

    private static final ScheduledActionLimitConfig ACTION_LIMIT_CONFIG =
            ScheduledActionLimitConfig.fromEnvironment();

    private static final PriceProbeRuntimeConfig PRICE_PROBE_CONFIG =
            PriceProbeRuntimeConfig.fromEnvironment();

    private final BotDetailsDto bot;
    private final BrowserManager browserManager;

    public ScheduledBotRunExecutor(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {
        if (bot == null
                || bot.getId() == null
                || bot.getId() <= 0) {
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
            throw new IllegalArgumentException(
                    "Scheduled job type is required."
            );
        }

        executeInternal(jobType);
    }

    private void executeInternal(ScheduledJobType jobType) {
        Long botId = bot.getId();
        BotContext context = new BotContext(bot, browserManager);
        boolean loginReady = false;

        try {
            if (jobType == ScheduledJobType.PRICE_PROBE) {
                if (!PRICE_PROBE_CONFIG.enabled()) {
                    log.info(
                            "[PRICE PROBE] Job for bot {} skipped because FLIPBOT_PRICE_PROBE_ENABLED=false.",
                            botId
                    );
                    return;
                }

                PriceProbeTestStealthProfile.installIfEnabled(
                        context.getBrowserContext()
                );

                new SandboxCloneLoginService(
                        context,
                        PRICE_PROBE_CONFIG
                ).login();
                loginReady = true;

                new PriceProbeProcessor(
                        context,
                        PRICE_PROBE_CONFIG
                ).processOne();
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

            boolean productionModeEnabled =
                    REAL_ACTION_CONFIG.productionModeEnabled();

            int maxRealOffersPerRun =
                    ACTION_LIMIT_CONFIG.effectiveMaxRealOffers(
                            productionModeEnabled
                    );

            int maxRealNextStepsPerRun =
                    ACTION_LIMIT_CONFIG.effectiveMaxRealNextSteps(
                            productionModeEnabled
                    );

            LoginService loginService = new LoginService(context);
            ListingClient listingClient = new ListingClient();
            OfferQuotaClient offerQuotaClient = new OfferQuotaClient();

            if (firstOfferRequested || nextStepRequested) {
                RealActionPreflight.Result preflight =
                        new RealActionPreflight().validate(
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

            ListingStatusUpdater listingStatusUpdater =
                    new ListingStatusUpdater(
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

            CatalogWorkProcessor catalogWorkProcessor =
                    new CatalogWorkProcessor(
                            context,
                            listingClient,
                            offerQuotaClient,
                            listingStatusUpdater,
                            realOffersEnabled,
                            maxRealOffersPerRun
                    );

            BotRunExecutor botRunExecutor =
                    new BotRunExecutor(
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

            if (jobType == null) {
                botRunExecutor.executeOneRun();
            } else {
                switch (jobType) {
                    case NEGOTIATION_CHECK ->
                            botRunExecutor.executeNegotiationCheck();
                    case CATALOG_SCAN ->
                            botRunExecutor.executeCatalogScan();
                    case PRICE_PROBE -> throw new IllegalStateException(
                            "PRICE_PROBE must use the isolated probe execution path."
                    );
                }
            }

        } finally {
            if (loginReady) {
                try {
                    context.saveSession();
                } catch (Exception exception) {
                    log.warn(
                            "[SCHEDULED JOB] Could not save session for bot {} before closing its context.",
                            botId,
                            exception
                    );
                }
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
        String jobLabel =
                jobType == null
                        ? "FULL_RUN"
                        : jobType.name();

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

        String modeLabel =
                REAL_ACTION_CONFIG.productionModeEnabled()
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

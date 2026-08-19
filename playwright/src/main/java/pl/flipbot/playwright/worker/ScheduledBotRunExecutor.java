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

@Slf4j
public class ScheduledBotRunExecutor {

    private static final ScheduledRealActionConfig REAL_ACTION_CONFIG =
            ScheduledRealActionConfig.fromEnvironment();

    private static final ScheduledActionLimitConfig ACTION_LIMIT_CONFIG =
            ScheduledActionLimitConfig.fromEnvironment();

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

    /**
     * Compatibility helper preserving the old full-run lifecycle.
     * Real actions are intentionally NEVER enabled through this helper.
     */
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

        BotContext context = new BotContext(bot, browserManager);
        boolean loginReady = false;

        try {
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

                    log.error(
                            "[REAL ACTION PREFLIGHT] Real actions downgraded to DRY RUN for bot {} / {} because preflight is BLOCKED.",
                            botId,
                            jobType
                    );
                }
            }

            if (REAL_ACTION_CONFIG.preflightOnly()
                    && (firstOfferRequested || nextStepRequested)) {
                realOffersEnabled = false;
                realNextStepsEnabled = false;

                log.warn(
                        "[REAL ACTION PREFLIGHT] PREFLIGHT ONLY is active for bot {} / {}. "
                                + "Validation may report READY, but real submit remains disabled.",
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
                    "[SCHEDULED JOB] Preparing {} for bot {} in PREFLIGHT ONLY / DRY RUN mode. "
                            + "requestedFirstOffer={}, requestedNextStep={}, realOffers=false, realNextSteps=false.",
                    jobLabel,
                    botId,
                    firstOfferRequested,
                    nextStepRequested
            );
            return;
        }

        if (!realOffersEnabled && !realNextStepsEnabled) {
            log.info(
                    "[SCHEDULED JOB] Preparing {} for bot {} in DRY RUN mode. "
                            + "realOffers=false, realNextSteps=false.",
                    jobLabel,
                    botId
            );
            return;
        }

        String modeLabel =
                REAL_ACTION_CONFIG.productionModeEnabled()
                        ? "PRODUCTION REAL ACTION MODE"
                        : "CONTROLLED REAL ACTION MODE";

        log.warn(
                "[SCHEDULED JOB] {} for {} / bot {}. "
                        + "realOffers={}, realNextSteps={}, firstOfferOneShotTestMode={}, "
                        + "maxRealOffersPerRun={}, maxRealNextStepsPerRun={}.",
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

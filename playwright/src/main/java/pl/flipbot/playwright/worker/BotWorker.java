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
import pl.flipbot.playwright.target.VintedRateLimitException;

@Slf4j
public class BotWorker implements Runnable {

    private static final boolean REAL_OFFERS_ENABLED =
            true;

    private static final Long REAL_OFFER_TEST_BOT_ID =
            4L;

    private static final boolean REAL_OFFER_ONE_SHOT_TEST_MODE =
            true;

    private static final boolean REAL_NEXT_STEPS_ENABLED =
            true;

    private static final int MAX_REAL_OFFERS_PER_RUN =
            5;

    private static final int MAX_REAL_NEXT_STEPS_PER_RUN =
            5;

    private static final long NORMAL_CYCLE_DELAY_MS =
            30_000L;

    private static final long RATE_LIMIT_COOLDOWN_MS =
            10L * 60L * 1_000L;


    private final BotContext context;

    private final LoginService loginService;

    private final BotRunExecutor botRunExecutor;

    private final boolean realOffersEnabledForThisBot;


    public BotWorker(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {

        this.context =
                new BotContext(
                        bot,
                        browserManager
                );


        this.realOffersEnabledForThisBot =
                REAL_OFFERS_ENABLED
                        && REAL_OFFER_TEST_BOT_ID.equals(
                        bot.getId()
                );


        this.loginService =
                new LoginService(
                        context
                );


        ListingClient listingClient =
                new ListingClient();


        OfferQuotaClient offerQuotaClient =
                new OfferQuotaClient();


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
                        REAL_NEXT_STEPS_ENABLED,
                        MAX_REAL_NEXT_STEPS_PER_RUN
                );


        CatalogWorkProcessor catalogWorkProcessor =
                new CatalogWorkProcessor(
                        context,
                        listingClient,
                        offerQuotaClient,
                        listingStatusUpdater,
                        realOffersEnabledForThisBot,
                        MAX_REAL_OFFERS_PER_RUN
                );


        this.botRunExecutor =
                new BotRunExecutor(
                        context,
                        existingNegotiationProcessor,
                        catalogWorkProcessor,
                        realOffersEnabledForThisBot,
                        REAL_OFFER_ONE_SHOT_TEST_MODE
                );
    }


    @Override
    public void run() {

        Long botId =
                context.getBot()
                        .getId();


        log.info(
                "Worker started for bot {}",
                botId
        );


        logSafetyConfiguration(
                botId
        );


        try {

            loginService.login();


            while (
                    !Thread.currentThread()
                            .isInterrupted()
            ) {

                long delayBeforeNextCycle =
                        NORMAL_CYCLE_DELAY_MS;


                try {

                    botRunExecutor.executeOneRun();

                } catch (VintedRateLimitException exception) {

                    delayBeforeNextCycle =
                            RATE_LIMIT_COOLDOWN_MS;


                    log.warn(
                            "[RATE LIMIT] Bot {} received an explicit Vinted "
                                    + "rate-limit page. This cycle is stopped. "
                                    + "The worker will perform no new work for "
                                    + "{} minutes before retrying.",
                            botId,
                            RATE_LIMIT_COOLDOWN_MS
                                    / 60_000L
                    );


                    log.debug(
                            "[RATE LIMIT] Full rate-limit exception for bot {}.",
                            botId,
                            exception
                    );

                } catch (Exception exception) {

                    log.error(
                            "[WORK CYCLE] Bot {} failed during this cycle. "
                                    + "The worker will retry normal worker work "
                                    + "in {} seconds.",
                            botId,
                            NORMAL_CYCLE_DELAY_MS
                                    / 1_000L,
                            exception
                    );
                }


                Thread.sleep(
                        delayBeforeNextCycle
                );
            }

        } catch (InterruptedException exception) {

            Thread.currentThread()
                    .interrupt();


            log.info(
                    "Worker {} was interrupted",
                    botId
            );

        } catch (Exception exception) {

            log.error(
                    "Worker {} stopped because of an unexpected error.",
                    botId,
                    exception
            );

        } finally {

            context.close();


            log.info(
                    "Worker stopped for bot {}",
                    botId
            );
        }
    }


    private void logSafetyConfiguration(
            Long botId
    ) {

        if (
                realOffersEnabledForThisBot
        ) {

            log.warn(
                    "[REAL OFFER TEST] REAL OFFERS ARE ENABLED for bot {}. "
                            + "One-shot mode={}, max real offers per run={}. "
                            + "Only bot {} is allowed to send a new real offer.",
                    botId,
                    REAL_OFFER_ONE_SHOT_TEST_MODE,
                    MAX_REAL_OFFERS_PER_RUN,
                    REAL_OFFER_TEST_BOT_ID
            );

        } else {

            log.info(
                    "[REAL OFFER TEST] Real offers are disabled for bot {}. "
                            + "Configured real-offer test bot is {}.",
                    botId,
                    REAL_OFFER_TEST_BOT_ID
            );
        }


        if (
                !REAL_NEXT_STEPS_ENABLED
        ) {

            log.info(
                    "[NEXT STEP] Real next negotiation steps are disabled."
            );
        }
    }
}

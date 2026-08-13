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

    /*
     * Scheduler mode is intentionally DRY RUN for real actions.
     * We do not reuse the old manual test flags here.
     */
    private static final boolean REAL_OFFERS_ENABLED =
            false;

    private static final boolean REAL_NEXT_STEPS_ENABLED =
            false;

    private static final boolean REAL_OFFER_ONE_SHOT_TEST_MODE =
            true;

    private static final int MAX_REAL_OFFERS_PER_RUN =
            1;

    private static final int MAX_REAL_NEXT_STEPS_PER_RUN =
            1;


    private final BotDetailsDto bot;

    private final BrowserManager browserManager;


    public ScheduledBotRunExecutor(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {

        if (
                bot == null
                        || bot.getId() == null
                        || bot.getId() <= 0
        ) {

            throw new IllegalArgumentException(
                    "Bot details with a positive ID are required."
            );
        }


        this.bot =
                bot;

        this.browserManager =
                browserManager;
    }


    public void executeOneRun() {

        Long botId =
                bot.getId();


        BotContext context =
                new BotContext(
                        bot,
                        browserManager
                );


        boolean loginReady =
                false;


        try {

            LoginService loginService =
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
                            REAL_OFFERS_ENABLED,
                            MAX_REAL_OFFERS_PER_RUN
                    );


            BotRunExecutor botRunExecutor =
                    new BotRunExecutor(
                            context,
                            existingNegotiationProcessor,
                            catalogWorkProcessor,
                            REAL_OFFERS_ENABLED,
                            REAL_OFFER_ONE_SHOT_TEST_MODE
                    );


            log.info(
                    "[SCHEDULED RUN] Preparing bot {} in DRY RUN mode.",
                    botId
            );


            loginService.login();

            loginReady =
                    true;


            botRunExecutor.executeOneRun();

        } finally {

            if (
                    loginReady
            ) {

                try {

                    context.saveSession();

                } catch (Exception exception) {

                    log.warn(
                            "[SCHEDULED RUN] Could not save session for bot {} before closing its context.",
                            botId,
                            exception
                    );
                }
            }


            context.close();
        }
    }
}

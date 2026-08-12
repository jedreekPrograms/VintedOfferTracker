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

    /*
     * Rozpoczynanie nowych negocjacji
     * dla listingów DISCOVERED.
     */
    private static final boolean REAL_OFFERS_ENABLED =
            false;

    /*
     * Wysyłanie kroku 2, 3 itd.
     * w istniejących rozmowach.
     */
    private static final boolean REAL_NEXT_STEPS_ENABLED =
            false;

    /*
     * Dodatkowe bezpieczniki na pojedynczy cykl workera.
     *
     * Prawdziwy dzienny limit ofert jest przechowywany
     * w backendzie/PostgreSQL.
     */
    private static final int MAX_REAL_OFFERS_PER_RUN =
            1;

    private static final int MAX_REAL_NEXT_STEPS_PER_RUN =
            1;


    private static final long NORMAL_CYCLE_DELAY_MS =
            30_000L;

    /*
     * Gdy Vinted jawnie pokaże "You are rate limited",
     * nie próbujemy dalej wysyłać requestów co 30 sekund.
     *
     * Worker respektuje blokadę i robi dłuższy cooldown.
     */
    private static final long RATE_LIMIT_COOLDOWN_MS =
            10L * 60L * 1_000L;


    private final BotContext context;

    private final LoginService loginService;

    private final ExistingNegotiationProcessor
            existingNegotiationProcessor;

    private final CatalogWorkProcessor
            catalogWorkProcessor;


    public BotWorker(
            BotDetailsDto bot,
            BrowserManager browserManager
    ) {

        this.context =
                new BotContext(
                        bot,
                        browserManager
                );


        this.loginService =
                new LoginService(
                        context
                );


        /*
         * Współdzielone zależności dla procesorów.
         *
         * Tworzymy je raz, żeby ExistingNegotiationProcessor
         * i CatalogWorkProcessor pracowały na tych samych
         * klientach API i updaterze statusów.
         */
        ListingClient listingClient =
                new ListingClient();


        OfferQuotaClient offerQuotaClient =
                new OfferQuotaClient();


        ListingStatusUpdater listingStatusUpdater =
                new ListingStatusUpdater(
                        context,
                        listingClient
                );


        this.existingNegotiationProcessor =
                new ExistingNegotiationProcessor(
                        context,
                        listingClient,
                        offerQuotaClient,
                        listingStatusUpdater,
                        REAL_NEXT_STEPS_ENABLED,
                        MAX_REAL_NEXT_STEPS_PER_RUN
                );


        this.catalogWorkProcessor =
                new CatalogWorkProcessor(
                        context,
                        listingClient,
                        offerQuotaClient,
                        listingStatusUpdater,
                        REAL_OFFERS_ENABLED,
                        MAX_REAL_OFFERS_PER_RUN
                );
    }


    @Override
    public void run() {

        log.info(
                "Worker started for bot {}",
                context.getBot().getId()
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

                    doWork();

                } catch (VintedRateLimitException exception) {

                    delayBeforeNextCycle =
                            RATE_LIMIT_COOLDOWN_MS;


                    log.warn(
                            "[RATE LIMIT] Bot {} received an explicit Vinted "
                                    + "rate-limit page. This cycle is stopped. "
                                    + "The worker will perform no new work for "
                                    + "{} minutes before retrying.",
                            context.getBot().getId(),
                            RATE_LIMIT_COOLDOWN_MS
                                    / 60_000L
                    );


                    log.debug(
                            "[RATE LIMIT] Full rate-limit exception for bot {}.",
                            context.getBot().getId(),
                            exception
                    );

                } catch (Exception exception) {

                    log.error(
                            "[WORK CYCLE] Bot {} failed during this cycle. "
                                    + "The worker will retry in {} seconds.",
                            context.getBot().getId(),
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
                    context.getBot().getId()
            );

        } catch (Exception exception) {

            log.error(
                    "Worker {} stopped because of an unexpected error.",
                    context.getBot().getId(),
                    exception
            );

        } finally {

            context.close();


            log.info(
                    "Worker stopped for bot {}",
                    context.getBot().getId()
            );
        }
    }


    private void doWork() {

        /*
         * 1. Najpierw obsługujemy już istniejące negocjacje.
         */
        boolean realNextStepWasSent =
                existingNegotiationProcessor.process();


        /*
         * Jeżeli w tym cyklu faktycznie wysłano kolejny
         * krok negocjacji, nie uruchamiamy dodatkowo
         * pracy na katalogu.
         */
        if (realNextStepWasSent) {

            log.warn(
                    "[NEXT STEP REAL] A real next negotiation step "
                            + "was sent during this run. "
                            + "The worker will not scan the catalog "
                            + "or start another negotiation."
            );

            return;
        }


        /*
         * 2. Cała praca katalogowa jest teraz
         * zamknięta w osobnym procesorze:
         *
         * katalog -> filtry -> scan -> CURRENT SCAN
         * -> PRICE GUARD -> nowe negocjacje.
         */
        catalogWorkProcessor.process();
    }
}
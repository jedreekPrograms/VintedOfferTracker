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
     * ============================================================
     * FIRST REAL OFFER TEST
     * ============================================================
     *
     * UWAGA:
     *
     * To jest specjalna konfiguracja do pierwszego
     * kontrolowanego testu prawdziwej oferty.
     *
     * Realne nowe negocjacje są globalnie włączone,
     * ale dodatkowy bezpiecznik pozwala na nie
     * TYLKO dla konkretnego bota:
     *
     * botId = 4
     */
    private static final boolean REAL_OFFERS_ENABLED =
            false;


    /*
     * TYLKO ten bot może podczas tego testu
     * rozpocząć prawdziwą nową negocjację.
     *
     * Jeżeli przypadkiem uruchomi się inny worker,
     * pozostanie on w DRY RUN.
     */
    private static final Long REAL_OFFER_TEST_BOT_ID =
            4L;


    /*
     * Najważniejszy dodatkowy bezpiecznik
     * pierwszego testu.
     *
     * true:
     *
     * worker dostaje tylko JEDEN katalogowy cykl,
     * w którym prawdziwa oferta może zostać wysłana.
     *
     * Po rozpoczęciu tego cyklu katalog zostaje
     * zablokowany aż do restartu aplikacji.
     *
     * Jest to celowo bardziej restrykcyjne
     * niż MAX_REAL_OFFERS_PER_RUN.
     */
    private static final boolean REAL_OFFER_ONE_SHOT_TEST_MODE =
            true;


    /*
     * ============================================================
     * EXISTING NEGOTIATIONS
     * ============================================================
     *
     * Kolejne kroki istniejących negocjacji
     * pozostają WYŁĄCZONE.
     *
     * Worker może je czytać i analizować,
     * ale nie wyśle automatycznie kroku 2, 3 itd.
     */
    private static final boolean REAL_NEXT_STEPS_ENABLED =
            false;


    /*
     * Nawet wewnątrz jedynego dozwolonego
     * katalogowego cyklu:
     *
     * maksymalnie jedna skutecznie rozpoczęta
     * prawdziwa negocjacja.
     */
    private static final int MAX_REAL_OFFERS_PER_RUN =
            1;


    /*
     * Obecnie nie ma znaczenia,
     * bo REAL_NEXT_STEPS_ENABLED=false.
     */
    private static final int MAX_REAL_NEXT_STEPS_PER_RUN =
            1;


    /*
     * Standardowy odstęp pomiędzy cyklami.
     *
     * Po pierwszym katalogowym cyklu worker nadal
     * będzie sprawdzał istniejące negocjacje,
     * ale nie uruchomi ponownie katalogu
     * w ONE_SHOT_TEST_MODE.
     */
    private static final long NORMAL_CYCLE_DELAY_MS =
            30_000L;


    /*
     * Jeżeli Vinted jawnie pokaże rate limit,
     * worker robi dłuższy cooldown.
     */
    private static final long RATE_LIMIT_COOLDOWN_MS =
            10L * 60L * 1_000L;


    private final BotContext context;

    private final LoginService loginService;

    private final ExistingNegotiationProcessor
            existingNegotiationProcessor;

    private final CatalogWorkProcessor
            catalogWorkProcessor;


    /*
     * Czy TEN konkretny worker ma prawo
     * wysłać realną ofertę.
     */
    private final boolean realOffersEnabledForThisBot;


    /*
     * ============================================================
     * ONE-SHOT SESSION GUARDS
     * ============================================================
     */

    /*
     * true od momentu rozpoczęcia pierwszego
     * katalogowego cyklu z real offers.
     *
     * Ustawiamy to PRZED catalogWorkProcessor.process().
     *
     * To jest celowe.
     *
     * Jeżeli np. wystąpi wyjątek już po kliknięciu
     * przycisku wysyłającego ofertę i stan będzie
     * niejednoznaczny, następny cykl NIE spróbuje
     * wysłać kolejnej oferty.
     */
    private boolean realOfferCatalogCycleConsumed =
            false;


    /*
     * Żeby nie spamować tego samego ostrzeżenia
     * co 30 sekund po zablokowaniu katalogu.
     */
    private boolean realOfferCatalogLockLogged =
            false;


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


        /*
         * Współdzielone zależności.
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


        /*
         * Istniejące negocjacje nadal są obsługiwane,
         * ale REAL_NEXT_STEPS_ENABLED pozostaje false.
         */
        this.existingNegotiationProcessor =
                new ExistingNegotiationProcessor(
                        context,
                        listingClient,
                        offerQuotaClient,
                        listingStatusUpdater,
                        REAL_NEXT_STEPS_ENABLED,
                        MAX_REAL_NEXT_STEPS_PER_RUN
                );


        /*
         * Bardzo ważne:
         *
         * nie przekazujemy tutaj po prostu
         * REAL_OFFERS_ENABLED.
         *
         * Przekazujemy:
         *
         * realOffersEnabledForThisBot
         *
         * więc nawet gdy globalnie test jest włączony,
         * realną ofertę może wysłać TYLKO bot 4.
         */
        this.catalogWorkProcessor =
                new CatalogWorkProcessor(
                        context,
                        listingClient,
                        offerQuotaClient,
                        listingStatusUpdater,
                        realOffersEnabledForThisBot,
                        MAX_REAL_OFFERS_PER_RUN
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


        /*
         * Wyraźny log bezpieczeństwa już na starcie.
         */
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

                    /*
                     * W ONE-SHOT real-offer test nawet jeżeli ten wyjątek
                     * wystąpi podczas katalogowego cyklu,
                     * realOfferCatalogCycleConsumed pozostanie true.
                     *
                     * Dzięki temu 30 sekund później nie uruchomimy
                     * kolejnej próby wysłania nowej oferty.
                     */
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


    private void doWork() {

        /*
         * ============================================================
         * 1. EXISTING NEGOTIATIONS
         * ============================================================
         *
         * Nadal sprawdzamy istniejące negocjacje.
         *
         * REAL_NEXT_STEPS_ENABLED=false,
         * więc nie powinien zostać wysłany
         * kolejny automatyczny krok.
         */
        boolean realNextStepWasSent =
                existingNegotiationProcessor.process();


        /*
         * Zachowujemy obecny safety guard.
         *
         * Gdyby kiedyś REAL_NEXT_STEPS_ENABLED
         * zostało włączone i krok faktycznie
         * został wysłany, nie robimy w tym samym
         * cyklu również nowej negocjacji.
         */
        if (
                realNextStepWasSent
        ) {

            log.warn(
                    "[NEXT STEP REAL] A real next negotiation step "
                            + "was sent during this run. "
                            + "The worker will not scan the catalog "
                            + "or start another negotiation."
            );


            return;
        }


        /*
         * ============================================================
         * 2. FIRST REAL OFFER ONE-SHOT LOCK
         * ============================================================
         *
         * Dotyczy tylko bota, dla którego
         * realne oferty są naprawdę włączone.
         */
        if (
                realOffersEnabledForThisBot
                        && REAL_OFFER_ONE_SHOT_TEST_MODE
                        && realOfferCatalogCycleConsumed
        ) {

            if (
                    !realOfferCatalogLockLogged
            ) {

                log.warn(
                        "[REAL OFFER TEST] One-shot catalog cycle has already "
                                + "been consumed for bot {}. "
                                + "Catalog scanning and NEW real negotiations "
                                + "are now locked until the worker is restarted. "
                                + "Existing negotiations may still be inspected.",
                        context.getBot()
                                .getId()
                );


                realOfferCatalogLockLogged =
                        true;
            }


            return;
        }


        /*
         * ============================================================
         * 3. ARM ONE-SHOT GUARD BEFORE CATALOG WORK
         * ============================================================
         *
         * Ustawiamy consumed=true PRZED wejściem
         * do katalogu.
         *
         * Dzięki temu nawet jeżeli później:
         *
         * - Vinted zachowa się dziwnie,
         * - wystąpi timeout,
         * - wyjątek wystąpi po reserveSlot(),
         * - stan wysłania oferty będzie niepewny,
         *
         * następny worker cycle nie spróbuje
         * rozpocząć kolejnej nowej negocjacji.
         */
        if (
                realOffersEnabledForThisBot
                        && REAL_OFFER_ONE_SHOT_TEST_MODE
        ) {

            realOfferCatalogCycleConsumed =
                    true;


            log.warn(
                    "[REAL OFFER TEST] Starting the ONLY catalog cycle "
                            + "allowed for bot {} during this worker session. "
                            + "After this point, no second catalog cycle "
                            + "will be allowed until restart.",
                    context.getBot()
                            .getId()
            );
        }


        /*
         * ============================================================
         * 4. CATALOG WORK
         * ============================================================
         *
         * katalog
         * -> filtry
         * -> scan
         * -> CURRENT SCAN
         * -> PRICE GUARD
         * -> TARGET MATCHER
         * -> mandatory FINAL VERIFY
         * -> reserveSlot()
         * -> NegotiationExecutor
         *
         * W naszym aktualnym NewNegotiationProcessor
         * maxRealOffersPerRun=1.
         */
        catalogWorkProcessor.process();


        /*
         * Sam catalogWorkProcessor może zakończyć się:
         *
         * - wysłaniem jednej prawdziwej oferty,
         * - OFFER_TOO_LOW,
         * - UNAVAILABLE,
         * - brakiem kandydatów,
         * - itd.
         *
         * Niezależnie od wyniku ONE_SHOT pozostaje
         * zużyty do restartu aplikacji.
         */
        if (
                realOffersEnabledForThisBot
                        && REAL_OFFER_ONE_SHOT_TEST_MODE
        ) {

            log.warn(
                    "[REAL OFFER TEST] The one-shot catalog cycle for bot {} "
                            + "finished. New catalog work is now locked "
                            + "until worker restart.",
                    context.getBot()
                            .getId()
            );
        }
    }
}
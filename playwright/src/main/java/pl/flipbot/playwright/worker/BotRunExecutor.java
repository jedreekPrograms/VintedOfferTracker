package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.negotiation.ExistingNegotiationProcessor;
import pl.flipbot.playwright.processing.CatalogWorkProcessor;

@Slf4j
public class BotRunExecutor {

    private final BotContext context;

    private final ExistingNegotiationProcessor
            existingNegotiationProcessor;

    private final CatalogWorkProcessor
            catalogWorkProcessor;

    private final boolean realOffersEnabledForThisBot;

    private final boolean realOfferOneShotTestMode;


    /*
     * Stan one-shot należy do wykonawcy kolejnych runów
     * w ramach jednej sesji workera.
     *
     * Dzięki temu wydzielenie pojedynczego runa nie zmienia
     * dotychczasowego zabezpieczenia przed drugą realną ofertą.
     */
    private boolean realOfferCatalogCycleConsumed =
            false;

    private boolean realOfferCatalogLockLogged =
            false;


    public BotRunExecutor(
            BotContext context,
            ExistingNegotiationProcessor existingNegotiationProcessor,
            CatalogWorkProcessor catalogWorkProcessor,
            boolean realOffersEnabledForThisBot,
            boolean realOfferOneShotTestMode
    ) {

        this.context =
                context;

        this.existingNegotiationProcessor =
                existingNegotiationProcessor;

        this.catalogWorkProcessor =
                catalogWorkProcessor;

        this.realOffersEnabledForThisBot =
                realOffersEnabledForThisBot;

        this.realOfferOneShotTestMode =
                realOfferOneShotTestMode;
    }


    /*
     * Jeden kompletny logiczny run bota.
     *
     * Ta metoda NIE:
     * - loguje się,
     * - nie robi sleep(),
     * - nie tworzy ani nie zamyka Playwrighta,
     * - nie posiada nieskończonej pętli.
     *
     * Dzięki temu scheduler będzie mógł w przyszłości
     * uruchamiać dokładnie jeden run i po jego zakończeniu
     * oddawać slot innemu botowi.
     */
    public void executeOneRun() {

        Long botId =
                context.getBot()
                        .getId();


        long startedAtNanos =
                System.nanoTime();


        log.info(
                "[BOT RUN] Starting one run for bot {}.",
                botId
        );


        try {

            executeExistingNegotiationsAndCatalog();

        } finally {

            long durationMs =
                    (System.nanoTime() - startedAtNanos)
                            / 1_000_000L;


            log.info(
                    "[BOT RUN] Finished one run for bot {} in {} ms.",
                    botId,
                    durationMs
            );
        }
    }


    private void executeExistingNegotiationsAndCatalog() {

        /*
         * ============================================================
         * 1. EXISTING NEGOTIATIONS
         * ============================================================
         *
         * To jest dokładnie pierwsza część starego BotWorker.doWork().
         */
        boolean realNextStepWasSent =
                existingNegotiationProcessor.process();


        /*
         * Jeżeli realny kolejny krok negocjacji został wysłany,
         * nie wykonujemy w tym samym runie również katalogu.
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
         */
        if (
                realOffersEnabledForThisBot
                        && realOfferOneShotTestMode
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
         * Nadal robimy to PRZED wejściem do katalogu.
         * Jeśli stan wysłania oferty stanie się niejednoznaczny,
         * kolejny run tej samej sesji nie wykona drugiej próby.
         */
        if (
                realOffersEnabledForThisBot
                        && realOfferOneShotTestMode
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
         * Cały istniejący pipeline pozostaje nietknięty:
         * katalog -> filtry -> scan -> target verification -> quota
         * -> negotiation executor.
         */
        catalogWorkProcessor.process();


        if (
                realOffersEnabledForThisBot
                        && realOfferOneShotTestMode
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

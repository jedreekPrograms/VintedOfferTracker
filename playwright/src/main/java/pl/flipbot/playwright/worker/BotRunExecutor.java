package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.negotiation.ExistingNegotiationProcessor;
import pl.flipbot.playwright.processing.CatalogWorkProcessor;

@Slf4j
public class BotRunExecutor {

    private final BotContext context;
    private final ExistingNegotiationProcessor existingNegotiationProcessor;
    private final CatalogWorkProcessor catalogWorkProcessor;
    private final boolean realOffersEnabledForThisBot;
    private final boolean realOfferOneShotTestMode;

    private boolean realOfferCatalogCycleConsumed = false;
    private boolean realOfferCatalogLockLogged = false;

    public BotRunExecutor(
            BotContext context,
            ExistingNegotiationProcessor existingNegotiationProcessor,
            CatalogWorkProcessor catalogWorkProcessor,
            boolean realOffersEnabledForThisBot,
            boolean realOfferOneShotTestMode
    ) {
        this.context = context;
        this.existingNegotiationProcessor = existingNegotiationProcessor;
        this.catalogWorkProcessor = catalogWorkProcessor;
        this.realOffersEnabledForThisBot = realOffersEnabledForThisBot;
        this.realOfferOneShotTestMode = realOfferOneShotTestMode;
    }

    /**
     * Compatibility wrapper for the legacy/manual continuous worker.
     * It preserves the old order: negotiations first, then catalog.
     */
    public void executeOneRun() {
        boolean realNextStepWasSent = executeNegotiationCheck();

        if (realNextStepWasSent) {
            log.warn(
                    "[NEXT STEP REAL] A real next negotiation step was sent during this run. "
                            + "The worker will not scan the catalog or start another negotiation."
            );
            return;
        }

        executeCatalogScan();
    }

    /**
     * Executes only the existing-negotiation part of a bot run.
     * No catalog navigation or new-listing scan is performed here.
     */
    public boolean executeNegotiationCheck() {
        Long botId = context.getBot().getId();
        long startedAtNanos = System.nanoTime();

        log.info(
                "[NEGOTIATION CHECK] Starting negotiation check for bot {}.",
                botId
        );

        try {
            return existingNegotiationProcessor.process();
        } finally {
            log.info(
                    "[NEGOTIATION CHECK] Finished negotiation check for bot {} in {} ms.",
                    botId,
                    elapsedMillis(startedAtNanos)
            );
        }
    }

    /**
     * Executes only catalog work. Existing negotiations are intentionally
     * not inspected by this method.
     */
    public void executeCatalogScan() {
        Long botId = context.getBot().getId();
        long startedAtNanos = System.nanoTime();

        log.info(
                "[CATALOG SCAN] Starting catalog scan for bot {}.",
                botId
        );

        try {
            executeCatalogWithSafetyGuards();
        } finally {
            log.info(
                    "[CATALOG SCAN] Finished catalog scan for bot {} in {} ms.",
                    botId,
                    elapsedMillis(startedAtNanos)
            );
        }
    }

    private void executeCatalogWithSafetyGuards() {
        if (realOffersEnabledForThisBot
                && realOfferOneShotTestMode
                && realOfferCatalogCycleConsumed) {

            if (!realOfferCatalogLockLogged) {
                log.warn(
                        "[REAL OFFER TEST] One-shot catalog cycle has already been consumed for bot {}. "
                                + "Catalog scanning and NEW real negotiations are locked until worker restart.",
                        context.getBot().getId()
                );
                realOfferCatalogLockLogged = true;
            }

            return;
        }

        if (realOffersEnabledForThisBot
                && realOfferOneShotTestMode) {

            realOfferCatalogCycleConsumed = true;

            log.warn(
                    "[REAL OFFER TEST] Starting the ONLY catalog cycle allowed for bot {} "
                            + "during this worker session.",
                    context.getBot().getId()
            );
        }

        catalogWorkProcessor.process();

        if (realOffersEnabledForThisBot
                && realOfferOneShotTestMode) {

            log.warn(
                    "[REAL OFFER TEST] The one-shot catalog cycle for bot {} finished. "
                            + "New catalog work is now locked until worker restart.",
                    context.getBot().getId()
            );
        }
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(
                0L,
                (System.nanoTime() - startedAtNanos) / 1_000_000L
        );
    }
}

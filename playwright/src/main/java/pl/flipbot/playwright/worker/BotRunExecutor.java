package pl.flipbot.playwright.worker;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.negotiation.ExistingNegotiationProcessor;
import pl.flipbot.playwright.processing.CatalogWorkProcessor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class BotRunExecutor {

    /**
     * Process-wide test safety gate.
     *
     * ScheduledBotRunExecutor/BotRunExecutor instances are recreated for
     * individual scheduler jobs and jobs may move between worker slots.
     * Therefore one-shot state cannot live in an instance field.
     *
     * The set intentionally survives all scheduler jobs in this JVM and is
     * cleared only by restarting the Playwright process.
     */
    private static final Set<Long> REAL_OFFER_ONE_SHOT_CONSUMED_BOTS =
            ConcurrentHashMap.newKeySet();

    private static final Set<Long> REAL_OFFER_ONE_SHOT_LOCK_LOGGED_BOTS =
            ConcurrentHashMap.newKeySet();

    private final BotContext context;
    private final ExistingNegotiationProcessor existingNegotiationProcessor;
    private final CatalogWorkProcessor catalogWorkProcessor;
    private final boolean realOffersEnabledForThisBot;
    private final boolean realOfferOneShotTestMode;

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
        Long botId = context.getBot().getId();

        if (isRealOfferOneShotArmed()
                && REAL_OFFER_ONE_SHOT_CONSUMED_BOTS.contains(botId)) {

            if (REAL_OFFER_ONE_SHOT_LOCK_LOGGED_BOTS.add(botId)) {
                log.warn(
                        "[REAL OFFER TEST] The process-wide one-shot real-offer allowance has already been consumed for bot {}. "
                                + "Catalog scanning and NEW real negotiations are locked for this bot until the Playwright process is restarted.",
                        botId
                );
            }

            return;
        }

        if (isRealOfferOneShotArmed()) {
            log.warn(
                    "[REAL OFFER TEST] Starting a controlled catalog cycle for bot {}. "
                            + "The process-wide one-shot allowance will be consumed only after the backend confirms a new NEGOTIATING listing. "
                            + "If this armed catalog cycle fails with an exception, the allowance will be consumed conservatively because submit state may be unknown.",
                    botId
            );
        }

        boolean newRealNegotiationStarted;

        try {
            newRealNegotiationStarted =
                    catalogWorkProcessor.process();

        } catch (RuntimeException exception) {
            if (isRealOfferOneShotArmed()) {
                consumeOneShot(botId);

                log.error(
                        "[REAL OFFER TEST] Armed catalog cycle for bot {} failed with an exception. "
                                + "The process-wide one-shot allowance is now locked until Playwright restart as a fail-closed precaution.",
                        botId
                );
            }

            throw exception;
        }

        if (!isRealOfferOneShotArmed()) {
            return;
        }

        if (newRealNegotiationStarted) {
            consumeOneShot(botId);

            log.warn(
                    "[REAL OFFER TEST] Backend confirmed that bot {} started a new NEGOTIATING listing. "
                            + "The process-wide one-shot allowance is now consumed. No further NEW real negotiation may start until Playwright restart.",
                    botId
            );

            return;
        }

        log.warn(
                "[REAL OFFER TEST] Controlled catalog cycle for bot {} finished without starting a new real negotiation. "
                        + "The one-shot allowance remains available for a later catalog cycle.",
                botId
        );
    }

    private boolean isRealOfferOneShotArmed() {
        return realOffersEnabledForThisBot
                && realOfferOneShotTestMode;
    }

    private void consumeOneShot(Long botId) {
        REAL_OFFER_ONE_SHOT_CONSUMED_BOTS.add(botId);
        REAL_OFFER_ONE_SHOT_LOCK_LOGGED_BOTS.remove(botId);
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(
                0L,
                (System.nanoTime() - startedAtNanos) / 1_000_000L
        );
    }
}

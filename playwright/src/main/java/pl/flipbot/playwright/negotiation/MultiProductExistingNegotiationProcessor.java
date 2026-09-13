package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.listing.TargetBoundListingClient;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.api.quota.RunScopedOfferQuotaClient;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotProductExecutionPlan;

/**
 * Runs the existing-negotiation workflow once per product while preserving one
 * bot/account session and one global per-run action cap. Inactive additional
 * products are still included so conversations started before disabling a
 * product keep their original strategy.
 */
@Slf4j
public class MultiProductExistingNegotiationProcessor
        extends ExistingNegotiationProcessor {

    private final BotContext context;
    private final ListingStatusUpdater listingStatusUpdater;
    private final boolean realNextStepsEnabled;
    private final int maxRealNextStepsPerRun;
    private final RunScopedOfferQuotaClient runQuota;

    public MultiProductExistingNegotiationProcessor(
            BotContext context,
            ListingClient listingClient,
            OfferQuotaClient offerQuotaClient,
            ListingStatusUpdater listingStatusUpdater,
            boolean realNextStepsEnabled,
            int maxRealNextStepsPerRun
    ) {
        super(
                context,
                listingClient,
                offerQuotaClient,
                listingStatusUpdater,
                realNextStepsEnabled,
                maxRealNextStepsPerRun
        );
        this.context = context;
        this.listingStatusUpdater = listingStatusUpdater;
        this.realNextStepsEnabled = realNextStepsEnabled;
        this.maxRealNextStepsPerRun = maxRealNextStepsPerRun;
        this.runQuota = new RunScopedOfferQuotaClient(
                offerQuotaClient,
                maxRealNextStepsPerRun
        );
    }

    @Override
    public boolean process() {
        BotConfigurationDto main = context.getBot().getConfiguration();
        if (main == null) {
            throw new IllegalStateException("Bot configuration is missing");
        }

        boolean sentAny = false;
        try {
            for (BotProductExecutionPlan.Target target :
                    BotProductExecutionPlan.negotiationTargets(context.getBot())) {
                context.getBot().setConfiguration(target.configuration());

                ListingClient targetClient = new TargetBoundListingClient(
                        target.additionalTargetId()
                );
                ExistingNegotiationProcessor delegate =
                        new ExistingNegotiationProcessor(
                                context,
                                targetClient,
                                runQuota,
                                listingStatusUpdater,
                                realNextStepsEnabled,
                                maxRealNextStepsPerRun
                        );

                log.info(
                        "[MULTI PRODUCT] Checking existing negotiations for bot {} product {}.",
                        context.getBot().getId(),
                        target.additionalTargetId() == null
                                ? "MAIN"
                                : target.additionalTargetId()
                );
                sentAny |= delegate.process();
            }
        } finally {
            context.getBot().setConfiguration(main);
        }
        return sentAny;
    }
}

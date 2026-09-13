package pl.flipbot.playwright.negotiation;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.listing.TargetBoundListingClient;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.api.quota.RunScopedOfferQuotaClient;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotAdditionalTargetDto;
import pl.flipbot.playwright.model.BotConfigurationDto;

import java.util.ArrayList;
import java.util.List;

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
            for (TargetExecution target : targets(main)) {
                context.getBot().setConfiguration(target.configuration());

                ListingClient targetClient = new TargetBoundListingClient(
                        target.id()
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
                        target.id() == null ? "MAIN" : target.id()
                );
                sentAny |= delegate.process();
            }
        } finally {
            context.getBot().setConfiguration(main);
        }
        return sentAny;
    }

    private List<TargetExecution> targets(BotConfigurationDto main) {
        List<TargetExecution> result = new ArrayList<>();
        result.add(new TargetExecution(null, main));

        List<BotAdditionalTargetDto> extras =
                context.getBot().getAdditionalTargets();
        if (extras == null) {
            return result;
        }

        for (BotAdditionalTargetDto extra : extras) {
            if (extra == null || extra.getAdditionalTargetId() == null) {
                continue;
            }
            result.add(new TargetExecution(
                    extra.getAdditionalTargetId(),
                    extra
            ));
        }
        return result;
    }

    private record TargetExecution(
            Long id,
            BotConfigurationDto configuration
    ) {
    }
}

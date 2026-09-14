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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runs the existing-negotiation workflow once per product while preserving one
 * bot/account session and one global per-run action cap. Product priority is
 * rotated between checks so a low real-action cap cannot permanently starve
 * later products. Inactive additional products may still be included by the
 * backend payload while conversations started before deactivation are running.
 */
@Slf4j
public class MultiProductExistingNegotiationProcessor
        extends ExistingNegotiationProcessor {

    private static final ConcurrentMap<Long, Integer> NEXT_PRODUCT_OFFSET =
            new ConcurrentHashMap<>();

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

        Long botId = context.getBot().getId();
        List<BotProductExecutionPlan.Target> targets = orderedTargetsForRun(
                botId,
                BotProductExecutionPlan.negotiationTargets(context.getBot())
        );

        log.info(
                "[MULTI PRODUCT] Bot {} checking negotiations for {} product(s) in rotated order: {}.",
                botId,
                targets.size(),
                targets.stream()
                        .map(target -> target.additionalTargetId() == null
                                ? "MAIN"
                                : target.additionalTargetId().toString())
                        .toList()
        );

        boolean sentAny = false;
        try {
            for (BotProductExecutionPlan.Target target : targets) {
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
                        botId,
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

    static List<BotProductExecutionPlan.Target> orderedTargetsForRun(
            Long botId,
            List<BotProductExecutionPlan.Target> targets
    ) {
        if (targets == null || targets.isEmpty()) {
            return List.of();
        }
        if (targets.size() == 1 || botId == null) {
            return List.copyOf(targets);
        }

        int offset = NEXT_PRODUCT_OFFSET.compute(
                botId,
                (ignored, previous) -> previous == null
                        ? 0
                        : (previous + 1) % targets.size()
        );

        List<BotProductExecutionPlan.Target> rotated = new ArrayList<>(
                targets.size()
        );
        for (int index = 0; index < targets.size(); index++) {
            rotated.add(targets.get((offset + index) % targets.size()));
        }
        return List.copyOf(rotated);
    }

    public static void clearRotationState(Long botId) {
        if (botId != null) {
            NEXT_PRODUCT_OFFSET.remove(botId);
        }
    }

    static void resetRotationForTests(Long botId) {
        clearRotationState(botId);
    }
}

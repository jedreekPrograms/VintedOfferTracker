package pl.flipbot.playwright.processing;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.listing.TargetBoundListingClient;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.api.quota.RunScopedOfferQuotaClient;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.FilterService;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.model.BotAdditionalTargetDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.negotiation.NewNegotiationProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
public class CatalogWorkProcessor {

    private static final ConcurrentMap<Long, Integer> NEXT_PRODUCT_OFFSET =
            new ConcurrentHashMap<>();

    private final BotContext context;
    private final ListingClient listingClient;
    private final ListingStatusUpdater listingStatusUpdater;
    private final boolean realOffersEnabled;
    private final int maxRealOffersPerRun;
    private final MarketplaceNavigator marketplaceNavigator;
    private final FilterService filterService;
    private final RunScopedOfferQuotaClient runQuota;

    public CatalogWorkProcessor(
            BotContext context,
            ListingClient listingClient,
            OfferQuotaClient offerQuotaClient,
            ListingStatusUpdater listingStatusUpdater,
            boolean realOffersEnabled,
            int maxRealOffersPerRun
    ) {
        this.context = context;
        this.listingClient = listingClient;
        this.listingStatusUpdater = listingStatusUpdater;
        this.realOffersEnabled = realOffersEnabled;
        this.maxRealOffersPerRun = maxRealOffersPerRun;
        this.marketplaceNavigator = new MarketplaceNavigator(context);
        this.filterService = new FilterService(context);
        this.runQuota = new RunScopedOfferQuotaClient(
                offerQuotaClient,
                maxRealOffersPerRun
        );
    }

    public boolean process() {
        Long botId = context.getBot().getId();
        BotConfigurationDto main = context.getBot().getConfiguration();
        if (main == null) {
            throw new IllegalStateException("Bot configuration is missing");
        }

        int before = realOffersEnabled
                ? listingClient.getNegotiatingListings(botId).size()
                : 0;

        List<TargetExecution> targets = targets(main);
        int offset = targets.size() <= 1
                ? 0
                : NEXT_PRODUCT_OFFSET.compute(
                        botId,
                        (ignored, previous) -> previous == null
                                ? 0
                                : (previous + 1) % targets.size()
                );

        log.info(
                "[MULTI PRODUCT] Bot {} scanning {} product(s), offset={}.",
                botId,
                targets.size(),
                offset
        );

        try {
            for (int i = 0; i < targets.size(); i++) {
                TargetExecution target = targets.get(
                        (offset + i) % targets.size()
                );
                context.getBot().setConfiguration(target.configuration());
                processTarget(target);
            }
        } finally {
            context.getBot().setConfiguration(main);
        }

        if (!realOffersEnabled) {
            return false;
        }

        int after = listingClient.getNegotiatingListings(botId).size();
        return after > before;
    }

    private void processTarget(TargetExecution target) {
        Long botId = context.getBot().getId();
        String label = target.id() == null
                ? "MAIN"
                : "ADDITIONAL:" + target.id();

        log.info(
                "[MULTI PRODUCT] Bot {} scanning {} brand='{}', model='{}'.",
                botId,
                label,
                target.configuration().getBrand(),
                target.configuration().getModel()
        );

        marketplaceNavigator.goToCatalog();
        filterService.applyFilters(context.getBot());

        ListingClient targetClient = new TargetBoundListingClient(target.id());
        CatalogCandidateProcessor.CandidateBatch batch =
                new CatalogCandidateProcessor(
                        context,
                        targetClient,
                        listingStatusUpdater
                ).process();

        if (batch.candidates().isEmpty()) {
            return;
        }

        new NewNegotiationProcessor(
                context,
                targetClient,
                runQuota,
                listingStatusUpdater,
                realOffersEnabled,
                maxRealOffersPerRun
        ).process(
                batch.candidates(),
                batch.currentScanListingIds()
        );
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
            if (extra != null
                    && extra.getAdditionalTargetId() != null
                    && Boolean.TRUE.equals(extra.getActive())) {
                result.add(new TargetExecution(
                        extra.getAdditionalTargetId(),
                        extra
                ));
            }
        }

        if (result.size() > 5) {
            throw new IllegalStateException(
                    "Bot has more than 4 active additional products"
            );
        }
        return result;
    }

    private record TargetExecution(
            Long id,
            BotConfigurationDto configuration
    ) {
    }
}

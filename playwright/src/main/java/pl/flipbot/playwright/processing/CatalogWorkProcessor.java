package pl.flipbot.playwright.processing;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.FilterService;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.negotiation.NewNegotiationProcessor;

@Slf4j
public class CatalogWorkProcessor {

    private final BotContext context;
    private final ListingClient listingClient;
    private final boolean realOffersEnabled;

    private final MarketplaceNavigator marketplaceNavigator;
    private final FilterService filterService;
    private final CatalogCandidateProcessor catalogCandidateProcessor;
    private final NewNegotiationProcessor newNegotiationProcessor;

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
        this.realOffersEnabled = realOffersEnabled;

        this.marketplaceNavigator = new MarketplaceNavigator(context);
        this.filterService = new FilterService(context);
        this.catalogCandidateProcessor = new CatalogCandidateProcessor(
                context,
                listingClient,
                listingStatusUpdater
        );
        this.newNegotiationProcessor = new NewNegotiationProcessor(
                context,
                listingClient,
                offerQuotaClient,
                listingStatusUpdater,
                realOffersEnabled,
                maxRealOffersPerRun
        );
    }

    /**
     * @return true only when this real-offer catalog cycle caused the backend
     * to contain more active NEGOTIATING listings than before the cycle.
     * Dry-run cycles always return false.
     */
    public boolean process() {
        Long botId = context.getBot().getId();

        int negotiatingBefore = realOffersEnabled
                ? listingClient.getNegotiatingListings(botId).size()
                : 0;

        /* 1. Open the live Vinted catalog. */
        marketplaceNavigator.goToCatalog();

        /* 2. Apply bot category / brand / model / price filters. */
        filterService.applyFilters(context.getBot());

        /*
         * 3. Build the candidate queue.
         *
         * CatalogCandidateProcessor:
         * - scans the current newest-first catalog page,
         * - persists genuinely new listings,
         * - loads the whole persisted DISCOVERED backlog,
         * - prioritizes current-scan items while interleaving old backlog,
         * - applies the hard stored-price guard,
         * - returns candidates for target/live verification.
         */
        var priceEligibleListings = catalogCandidateProcessor.process();

        if (priceEligibleListings.isEmpty()) {
            log.info(
                    "[CATALOG WORK] No eligible DISCOVERED candidates remain after backlog selection and price guards."
            );
            return false;
        }

        /*
         * 4. Verify and start as many safe first offers as this production
         * run is allowed to perform. The processor still enforces all
         * per-listing live checks, persistent guard and quota semantics.
         */
        newNegotiationProcessor.process(priceEligibleListings);

        if (!realOffersEnabled) {
            return false;
        }

        int negotiatingAfter =
                listingClient.getNegotiatingListings(botId).size();

        boolean newNegotiationStarted =
                negotiatingAfter > negotiatingBefore;

        log.info(
                "[REAL OFFER RESULT] Active negotiations before catalog={}, after catalog={}. New negotiation started={}.",
                negotiatingBefore,
                negotiatingAfter,
                newNegotiationStarted
        );

        return newNegotiationStarted;
    }
}

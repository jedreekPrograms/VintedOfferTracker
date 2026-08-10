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

    private final MarketplaceNavigator
            marketplaceNavigator;

    private final FilterService
            filterService;

    private final CatalogCandidateProcessor
            catalogCandidateProcessor;

    private final NewNegotiationProcessor
            newNegotiationProcessor;


    public CatalogWorkProcessor(
            BotContext context,
            ListingClient listingClient,
            OfferQuotaClient offerQuotaClient,
            ListingStatusUpdater listingStatusUpdater,
            boolean realOffersEnabled,
            int maxRealOffersPerRun
    ) {

        this.context =
                context;


        this.marketplaceNavigator =
                new MarketplaceNavigator(
                        context
                );


        this.filterService =
                new FilterService(
                        context
                );


        this.catalogCandidateProcessor =
                new CatalogCandidateProcessor(
                        context,
                        listingClient,
                        listingStatusUpdater
                );


        this.newNegotiationProcessor =
                new NewNegotiationProcessor(
                        context,
                        listingClient,
                        offerQuotaClient,
                        listingStatusUpdater,
                        realOffersEnabled,
                        maxRealOffersPerRun
                );
    }


    public void process() {

        /*
         * 1. Otwieramy katalog.
         */
        marketplaceNavigator.goToCatalog();


        /*
         * 2. Nakładamy konfigurację bota:
         * kategoria / marka / model / cena itd.
         */
        filterService.applyFilters(
                context.getBot()
        );


        /*
         * 3. CatalogCandidateProcessor:
         *
         * - skanuje aktualnie widoczne oferty,
         * - wysyła je do backendu,
         * - przecina DISCOVERED z CURRENT SCAN,
         * - wykonuje PRICE GUARD,
         * - zwraca tylko bezpiecznych kandydatów.
         */
        var priceEligibleListings =
                catalogCandidateProcessor.process();


        if (priceEligibleListings.isEmpty()) {

            log.info(
                    "[CATALOG WORK] No eligible listings remain "
                            + "after current-scan and price guards."
            );

            return;
        }


        /*
         * 4. NewNegotiationProcessor:
         *
         * - sprawdza capacity,
         * - obsługuje dry run,
         * - rezerwuje quota,
         * - rozpoczyna pierwszą ofertę,
         * - obsługuje UNAVAILABLE / OFFER_TOO_LOW.
         */
        newNegotiationProcessor.process(
                priceEligibleListings
        );
    }
}
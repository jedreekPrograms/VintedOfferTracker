package pl.flipbot.playwright.processing;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.quota.OfferQuotaClient;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.FilterService;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.negotiation.NewNegotiationProcessor;

@Slf4j
public class CatalogWorkProcessor {

    private static final String VINTED_MODEL = "VINTED_MODEL";
    private static final String SEARCH_QUERY = "SEARCH_QUERY";

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

        logTargetExecutionPlan(botId);

        log.info("[BOT TARGET FLOW] Bot {} -> opening Vinted catalog.", botId);
        marketplaceNavigator.goToCatalog();

        filterService.applyFilters(context.getBot());

        BotConfigurationDto configuration = context.getBot().getConfiguration();
        String targetMode = resolveTargetMode(configuration);

        log.info(
                "[BOT TARGET FLOW] Bot {} target setup VERIFIED. mode={}, finalCatalogUrl={}",
                botId,
                targetMode,
                context.getPage().url()
        );

        log.info(
                "[BOT TARGET FLOW] Bot {} -> scanning current catalog and loading only this bot's persisted DISCOVERED pool.",
                botId
        );

        CatalogCandidateProcessor.CandidateBatch candidateBatch =
                catalogCandidateProcessor.process();

        if (candidateBatch.candidates().isEmpty()) {
            log.info(
                    "[CATALOG WORK] Bot {} has no eligible DISCOVERED candidates after backlog selection and price guards.",
                    botId
            );
            return false;
        }

        log.info(
                "[BOT TARGET FLOW] Bot {} -> {} price-eligible candidate(s) reached target verification. Current filtered scan contains {} IDs; persisted-only backlog will not inherit that proof.",
                botId,
                candidateBatch.candidates().size(),
                candidateBatch.currentScanListingIds().size()
        );

        newNegotiationProcessor.process(
                candidateBatch.candidates(),
                candidateBatch.currentScanListingIds()
        );

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

    private void logTargetExecutionPlan(Long botId) {
        BotConfigurationDto configuration = context.getBot().getConfiguration();

        if (configuration == null) {
            log.warn(
                    "[BOT TARGET FLOW] Bot {} has no configuration. Catalog processing will fail closed.",
                    botId
            );
            return;
        }

        String targetMode = resolveTargetMode(configuration);

        log.info(
                "[BOT TARGET FLOW] ==================== BOT {} CATALOG PLAN ====================",
                botId
        );
        log.info(
                "[BOT TARGET FLOW] categoryPath={}, brand='{}', minPrice={}, maxPrice={}, targetMode={}",
                configuration.getCategoryPath(),
                configuration.getBrand(),
                configuration.getMinPrice(),
                configuration.getMaxPrice(),
                targetMode
        );

        if (SEARCH_QUERY.equals(targetMode)) {
            log.info(
                    "[BOT TARGET FLOW] MODE=SEARCH_QUERY -> TEXT SEARCH is ACTIVE; Vinted model filter is SKIPPED."
            );
            log.info(
                    "[BOT TARGET FLOW] Bot {} will type searchQuery='{}' into Vinted input form[action='/catalog'] input[name='search_text'], read the value back, press Enter, and verify search_text in the resulting URL.",
                    botId,
                    configuration.getSearchQuery()
            );
            log.info(
                    "[BOT TARGET FLOW] After text search: category -> brand -> price range -> newest_first. Configured model='{}' is NOT used as a Vinted model filter in this mode.",
                    configuration.getModel()
            );
        } else if (VINTED_MODEL.equals(targetMode)) {
            log.info(
                    "[BOT TARGET FLOW] MODE=VINTED_MODEL -> VINTED MODEL FILTER is ACTIVE; catalog text search is SKIPPED."
            );
            log.info(
                    "[BOT TARGET FLOW] Bot {} will apply category -> brand -> open Vinted model filter -> type model='{}' into the model-filter search -> click ONLY the exact model option -> confirm -> verify brand_collection_ids[] in URL -> price range -> newest_first.",
                    botId,
                    configuration.getModel()
            );
            log.info(
                    "[BOT TARGET FLOW] Only listing IDs actually observed in that verified current result set receive current exact-model provenance. Older DISCOVERED rows stay bot-scoped backlog and must be revalidated before a real action."
            );
            log.info(
                    "[BOT TARGET FLOW] Configured searchQuery='{}' is NOT typed into the main Vinted search box in this mode.",
                    configuration.getSearchQuery()
            );
        } else {
            log.warn(
                    "[BOT TARGET FLOW] Bot {} has unsupported targetMode='{}'. FilterService will fail closed.",
                    botId,
                    targetMode
            );
        }
    }

    private String resolveTargetMode(BotConfigurationDto configuration) {
        if (configuration == null
                || configuration.getTargetMode() == null
                || configuration.getTargetMode().isBlank()) {
            return VINTED_MODEL;
        }
        return configuration.getTargetMode().trim().toUpperCase();
    }
}

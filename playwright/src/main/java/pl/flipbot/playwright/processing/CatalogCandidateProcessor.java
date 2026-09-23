package pl.flipbot.playwright.processing;

import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.ListingClient;
import pl.flipbot.playwright.api.listing.ListingStatusUpdater;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.scanner.ListingScanner;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class CatalogCandidateProcessor {

    private static final int FRESH_PRIORITY_BATCH = 2;
    private static final int BACKLOG_PROGRESS_BATCH = 1;

    private final BotContext context;
    private final ListingClient listingClient;
    private final ListingStatusUpdater listingStatusUpdater;
    private final ListingScanner listingScanner;
    private final ListingProcessingService listingProcessingService;

    public CatalogCandidateProcessor(
            BotContext context,
            ListingClient listingClient,
            ListingStatusUpdater listingStatusUpdater
    ) {
        this.context = context;
        this.listingClient = listingClient;
        this.listingStatusUpdater = listingStatusUpdater;
        this.listingScanner = new ListingScanner(context);
        this.listingProcessingService = new ListingProcessingService(
                context,
                listingClient
        );
    }

    /**
     * Builds one bot's candidate pool and carries CURRENT-SCAN provenance next
     * to the prioritized persisted backlog.
     *
     * The backend endpoint is already bot-scoped: bot 4 loads bot 4 DISCOVERED,
     * bot 9 loads bot 9 DISCOVERED, etc. The important extra distinction is
     * that belonging to a bot's persisted backlog is NOT by itself proof of
     * the bot's current model configuration.
     */
    public CandidateBatch process() {
        Long botId = context.getBot().getId();

        var scannedListings = listingScanner.scan();

        Set<String> currentScanListingIds = new LinkedHashSet<>();
        for (var scannedListing : scannedListings) {
            if (scannedListing.getId() != null) {
                currentScanListingIds.add(scannedListing.getId());
            }
        }

        log.info(
                "[CATALOG CANDIDATES] Bot {} current filtered scan contains {} unique marketplace listings. Only these IDs carry current-scan target provenance in this cycle.",
                botId,
                currentScanListingIds.size()
        );

        if (currentScanListingIds.isEmpty()) {
            log.warn(
                    "[CATALOG CANDIDATES] Bot {} current filtered scan is empty. Persisted DISCOVERED backlog may still be considered, but NONE of it will inherit current-filter model proof.",
                    botId
            );
        }

        var newlyClaimedListings = listingProcessingService.process(scannedListings);

        Set<String> newlyClaimedListingIds = new LinkedHashSet<>();
        for (ListingResponseDto listing : newlyClaimedListings) {
            if (listing != null
                    && listing.listingId() != null
                    && !listing.listingId().isBlank()) {
                newlyClaimedListingIds.add(listing.listingId());
            }
        }

        log.info(
                "[CATALOG CANDIDATES] Bot {} claimed {} genuinely new listings.",
                botId,
                newlyClaimedListingIds.size()
        );

        /*
         * This endpoint is explicitly scoped by botId. Each bot therefore has
         * its own persisted DISCOVERED pool. Old bad rows can still exist in
         * that pool from historical filter bugs/config changes, so target proof
         * is tracked separately through currentScanListingIds.
         */
        List<ListingResponseDto> discoveredListings =
                listingClient.getDiscoveredListings(botId);

        CandidateSelection candidateSelection = selectCandidates(
                discoveredListings,
                currentScanListingIds,
                newlyClaimedListingIds
        );

        log.info(
                "[CATALOG CANDIDATES] Bot {} DISCOVERED pool: backend={}, genuinelyNew={}, backlog={}. Processing starts with the oldest backlog item, then uses {} fresh : {} backlog fairness batches.",
                botId,
                discoveredListings.size(),
                candidateSelection.fresh().size(),
                candidateSelection.backlog().size(),
                FRESH_PRIORITY_BATCH,
                BACKLOG_PROGRESS_BATCH
        );

        List<ListingResponseDto> prioritizedCandidates =
                candidateSelection.prioritized();

        if (prioritizedCandidates.isEmpty()) {
            log.info(
                    "[CATALOG CANDIDATES] Bot {} has no DISCOVERED listings eligible for further processing.",
                    botId
            );
            return new CandidateBatch(
                    List.of(),
                    Set.copyOf(currentScanListingIds)
            );
        }

        List<ListingResponseDto> priceEligibleListings = new ArrayList<>();
        int skippedOutsidePriceRange = 0;

        for (ListingResponseDto listing : prioritizedCandidates) {
            if (isListingWithinConfiguredPriceRange(listing)) {
                priceEligibleListings.add(listing);
                continue;
            }

            listingStatusUpdater.markOutsidePriceRange(
                    botId,
                    listing
            );
            skippedOutsidePriceRange++;
        }

        log.info(
                "[PRICE GUARD] Bot {} checked {} DISCOVERED candidates. Eligible={}, skipped={}.",
                botId,
                prioritizedCandidates.size(),
                priceEligibleListings.size(),
                skippedOutsidePriceRange
        );

        if (priceEligibleListings.isEmpty()) {
            log.info(
                    "[PRICE GUARD] Bot {} has no DISCOVERED listings inside the configured price range.",
                    botId
            );
            return new CandidateBatch(
                    List.of(),
                    Set.copyOf(currentScanListingIds)
            );
        }

        long currentlyProven = priceEligibleListings.stream()
                .filter(listing -> currentScanListingIds.contains(listing.listingId()))
                .count();

        log.info(
                "[CATALOG CANDIDATES] Bot {} has {} listings ready for target/live verification. Current-scan proven IDs={}, persisted-only backlog IDs={}.",
                botId,
                priceEligibleListings.size(),
                currentlyProven,
                priceEligibleListings.size() - currentlyProven
        );

        return new CandidateBatch(
                List.copyOf(priceEligibleListings),
                Set.copyOf(currentScanListingIds)
        );
    }

    static CandidateSelection selectCandidates(
            List<ListingResponseDto> discoveredListings,
            Set<String> currentScanListingIds,
            Set<String> newlyClaimedListingIds
    ) {
        if (discoveredListings == null || discoveredListings.isEmpty()) {
            return new CandidateSelection(
                    List.of(),
                    List.of(),
                    List.of()
            );
        }

        Set<String> currentIds = currentScanListingIds == null
                ? Set.of()
                : currentScanListingIds;
        Set<String> newIds = newlyClaimedListingIds == null
                ? Set.of()
                : newlyClaimedListingIds;

        Map<String, ListingResponseDto> remainingByMarketplaceId =
                new LinkedHashMap<>();

        for (ListingResponseDto listing : discoveredListings) {
            if (listing == null
                    || listing.listingId() == null
                    || listing.listingId().isBlank()) {
                continue;
            }

            remainingByMarketplaceId.putIfAbsent(
                    listing.listingId(),
                    listing
            );
        }

        List<ListingResponseDto> fresh = new ArrayList<>();

        for (String marketplaceListingId : currentIds) {
            if (!newIds.contains(marketplaceListingId)) {
                continue;
            }

            ListingResponseDto current =
                    remainingByMarketplaceId.remove(marketplaceListingId);

            if (current != null) {
                fresh.add(current);
            }
        }

        /*
         * Freshness controls queue ordering only. A previously discovered item
         * that is still present in the current scan remains backlog for fairness
         * but separately retains current-scan target provenance.
         */
        List<ListingResponseDto> backlog =
                new ArrayList<>(remainingByMarketplaceId.values());

        List<ListingResponseDto> prioritized = interleaveFairly(
                fresh,
                backlog
        );

        return new CandidateSelection(
                List.copyOf(fresh),
                List.copyOf(backlog),
                List.copyOf(prioritized)
        );
    }

    private static List<ListingResponseDto> interleaveFairly(
            List<ListingResponseDto> fresh,
            List<ListingResponseDto> backlog
    ) {
        List<ListingResponseDto> result = new ArrayList<>(
                fresh.size() + backlog.size()
        );

        int freshIndex = 0;
        int backlogIndex = 0;

        if (backlogIndex < backlog.size()) {
            result.add(backlog.get(backlogIndex++));
        }

        while (freshIndex < fresh.size()
                || backlogIndex < backlog.size()) {

            for (int count = 0;
                 count < FRESH_PRIORITY_BATCH
                         && freshIndex < fresh.size();
                 count++) {
                result.add(fresh.get(freshIndex++));
            }

            for (int count = 0;
                 count < BACKLOG_PROGRESS_BATCH
                         && backlogIndex < backlog.size();
                 count++) {
                result.add(backlog.get(backlogIndex++));
            }
        }

        return result;
    }

    private boolean isListingWithinConfiguredPriceRange(
            ListingResponseDto listing
    ) {
        BigDecimal listingPrice = listing.originalPrice();

        if (listingPrice == null) {
            log.warn(
                    "[PRICE GUARD] Marketplace listing {} has no price. It will not be negotiated.",
                    listing.listingId()
            );
            return false;
        }

        BotConfigurationDto configuration = context.getBot().getConfiguration();
        if (configuration == null) {
            throw new IllegalStateException("Bot configuration is missing");
        }

        BigDecimal minPrice = configuration.getMinPrice();
        BigDecimal maxPrice = configuration.getMaxPrice();

        if (minPrice != null && listingPrice.compareTo(minPrice) < 0) {
            log.info(
                    "[PRICE GUARD] Skipping marketplace listing {}. Price {} is below configured minimum {}.",
                    listing.listingId(),
                    listingPrice,
                    minPrice
            );
            return false;
        }

        if (maxPrice != null && listingPrice.compareTo(maxPrice) > 0) {
            log.info(
                    "[PRICE GUARD] Skipping marketplace listing {}. Price {} is above configured maximum {}.",
                    listing.listingId(),
                    listingPrice,
                    maxPrice
            );
            return false;
        }

        return true;
    }

    record CandidateBatch(
            List<ListingResponseDto> candidates,
            Set<String> currentScanListingIds
    ) {
    }

    record CandidateSelection(
            List<ListingResponseDto> fresh,
            List<ListingResponseDto> backlog,
            List<ListingResponseDto> prioritized
    ) {
    }
}

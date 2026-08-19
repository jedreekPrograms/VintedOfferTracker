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

    /*
     * Fresh listings should remain the priority, but older DISCOVERED
     * listings must never starve just because they fell off page 1.
     *
     * A 3:2 merge gives every catalog cycle room for both groups while
     * preserving Vinted's newest-first ordering inside the current scan and
     * backend id ordering inside the backlog.
     */
    private static final int CURRENT_SCAN_PRIORITY_BATCH = 3;
    private static final int BACKLOG_PROGRESS_BATCH = 2;

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

    public List<ListingResponseDto> process() {
        Long botId = context.getBot().getId();

        /*
         * 1. Scan what is visible in the current filtered Vinted catalog.
         * LinkedHashSet intentionally preserves newest-first scan ordering.
         */
        var scannedListings = listingScanner.scan();

        Set<String> currentScanListingIds = new LinkedHashSet<>();
        for (var scannedListing : scannedListings) {
            if (scannedListing.getId() != null) {
                currentScanListingIds.add(scannedListing.getId());
            }
        }

        log.info(
                "[CATALOG CANDIDATES] Current scan contains {} unique marketplace listings.",
                currentScanListingIds.size()
        );

        if (currentScanListingIds.isEmpty()) {
            log.warn(
                    "[CATALOG CANDIDATES] Current filtered scan is empty. "
                            + "No new listings can be discovered this cycle, but the persisted DISCOVERED backlog will still be considered safely."
            );
        }

        /*
         * 2. Persist genuinely new current-scan listings.
         * The backend remains authoritative for global listing ownership.
         */
        var newlyClaimedListings = listingProcessingService.process(scannedListings);

        log.info(
                "[CATALOG CANDIDATES] Bot {} claimed {} new listings.",
                botId,
                newlyClaimedListings.size()
        );

        /*
         * 3. Load the whole DISCOVERED backlog for this bot.
         *
         * IMPORTANT: an older DISCOVERED listing is NOT discarded merely
         * because it has fallen off the current first catalog page. It still
         * has a stored direct URL and will go through target verification,
         * availability checks and all real-action guards before submit.
         */
        List<ListingResponseDto> discoveredListings =
                listingClient.getDiscoveredListings(botId);

        CandidateSelection candidateSelection = selectCandidates(
                discoveredListings,
                currentScanListingIds
        );

        log.info(
                "[CATALOG CANDIDATES] DISCOVERED listings: backend={}, currentScan={}, backlog={}. "
                        + "Processing order uses {} fresh : {} backlog fairness batches.",
                discoveredListings.size(),
                candidateSelection.currentScan().size(),
                candidateSelection.backlog().size(),
                CURRENT_SCAN_PRIORITY_BATCH,
                BACKLOG_PROGRESS_BATCH
        );

        List<ListingResponseDto> prioritizedCandidates =
                candidateSelection.prioritized();

        if (prioritizedCandidates.isEmpty()) {
            log.info(
                    "[CATALOG CANDIDATES] Bot {} has no DISCOVERED listings eligible for further processing.",
                    botId
            );
            return List.of();
        }

        /*
         * 4. Hard price guard over BOTH fresh and backlog candidates.
         *
         * We do not trust only Vinted's catalog filter. Stored listing data
         * must still satisfy the configured range before any item-page work.
         */
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
                "[PRICE GUARD] Checked {} DISCOVERED candidates. Eligible={}, skipped={}.",
                prioritizedCandidates.size(),
                priceEligibleListings.size(),
                skippedOutsidePriceRange
        );

        if (priceEligibleListings.isEmpty()) {
            log.info(
                    "[PRICE GUARD] Bot {} has no DISCOVERED listings inside the configured price range.",
                    botId
            );
            return List.of();
        }

        log.info(
                "[CATALOG CANDIDATES] Bot {} has {} listings ready for target/live verification.",
                botId,
                priceEligibleListings.size()
        );

        return priceEligibleListings;
    }

    static CandidateSelection selectCandidates(
            List<ListingResponseDto> discoveredListings,
            Set<String> currentScanListingIds
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

        List<ListingResponseDto> currentScan = new ArrayList<>();

        /*
         * Iterate currentIds, not discoveredListings: this preserves the
         * newest-first ordering produced by the live Vinted scan.
         */
        for (String marketplaceListingId : currentIds) {
            ListingResponseDto current =
                    remainingByMarketplaceId.remove(marketplaceListingId);

            if (current != null) {
                currentScan.add(current);
            }
        }

        /*
         * getDiscoveredListings() is backend-id ASC, so the remaining values
         * form an oldest-first backlog. Old items therefore eventually drain
         * instead of being permanently starved by newer catalog pages.
         */
        List<ListingResponseDto> backlog =
                new ArrayList<>(remainingByMarketplaceId.values());

        List<ListingResponseDto> prioritized = interleaveFairly(
                currentScan,
                backlog
        );

        return new CandidateSelection(
                List.copyOf(currentScan),
                List.copyOf(backlog),
                List.copyOf(prioritized)
        );
    }

    private static List<ListingResponseDto> interleaveFairly(
            List<ListingResponseDto> currentScan,
            List<ListingResponseDto> backlog
    ) {
        List<ListingResponseDto> result = new ArrayList<>(
                currentScan.size() + backlog.size()
        );

        int currentIndex = 0;
        int backlogIndex = 0;

        while (currentIndex < currentScan.size()
                || backlogIndex < backlog.size()) {

            for (int count = 0;
                    count < CURRENT_SCAN_PRIORITY_BATCH
                            && currentIndex < currentScan.size();
                    count++) {
                result.add(currentScan.get(currentIndex++));
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

    record CandidateSelection(
            List<ListingResponseDto> currentScan,
            List<ListingResponseDto> backlog,
            List<ListingResponseDto> prioritized
    ) {
    }
}

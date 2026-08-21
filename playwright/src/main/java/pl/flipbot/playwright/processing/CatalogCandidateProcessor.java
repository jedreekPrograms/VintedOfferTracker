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
     * A listing that is already DISCOVERED but has never started a
     * negotiation must not starve just because newer items keep appearing.
     *
     * Every cycle starts with the oldest persisted DISCOVERED backlog item,
     * then gives two slots to genuinely new listings before progressing the
     * backlog again. This guarantees backlog progress even when the real-offer
     * run limit is only one, while still keeping most capacity focused on new
     * marketplace offers.
     */
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
         * The backend remains authoritative for listing identity within this
         * bot. A listing already known to this bot is intentionally not
         * recreated and remains in its existing lifecycle state.
         */
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
         * 3. Load the whole DISCOVERED backlog for this bot.
         *
         * IMPORTANT: a listing is backlog whenever it was already DISCOVERED
         * before this scan, even if it still appears on the current Vinted
         * page. That distinction prevents a yesterday listing from being
         * treated as forever "fresh" and pushed behind tomorrow's new items.
         *
         * An older DISCOVERED listing is NOT discarded merely because it has
         * fallen off the current first catalog page. It still has a stored
         * direct URL and will go through target verification, availability
         * checks and all real-action guards before submit.
         */
        List<ListingResponseDto> discoveredListings =
                listingClient.getDiscoveredListings(botId);

        CandidateSelection candidateSelection = selectCandidates(
                discoveredListings,
                currentScanListingIds,
                newlyClaimedListingIds
        );

        log.info(
                "[CATALOG CANDIDATES] DISCOVERED listings: backend={}, genuinelyNew={}, backlog={}. "
                        + "Processing starts with the oldest backlog item, then uses {} fresh : {} backlog fairness batches.",
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

        /*
         * Fresh means genuinely claimed during THIS scan, not merely visible
         * in the current catalog. Iterate currentIds so genuinely new items
         * retain Vinted's newest-first ordering.
         */
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
         * getDiscoveredListings() is backend-id ASC. Every remaining value was
         * already DISCOVERED before this scan, so the remaining values form an
         * oldest-first backlog regardless of whether they are still visible
         * on page 1. Old items therefore cannot be permanently starved by a
         * continuous stream of newer listings.
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

        /*
         * The oldest outstanding candidate goes first. This is what makes the
         * guarantee hold even when capacity/maxRealOffersPerRun is only one.
         */
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

    record CandidateSelection(
            List<ListingResponseDto> fresh,
            List<ListingResponseDto> backlog,
            List<ListingResponseDto> prioritized
    ) {
    }
}

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class CatalogCandidateProcessor {

    private final BotContext context;

    private final ListingClient listingClient;

    private final ListingStatusUpdater
            listingStatusUpdater;

    private final ListingScanner
            listingScanner;

    private final ListingProcessingService
            listingProcessingService;


    public CatalogCandidateProcessor(
            BotContext context,
            ListingClient listingClient,
            ListingStatusUpdater listingStatusUpdater
    ) {

        this.context =
                context;

        this.listingClient =
                listingClient;

        this.listingStatusUpdater =
                listingStatusUpdater;

        this.listingScanner =
                new ListingScanner(
                        context
                );

        this.listingProcessingService =
                new ListingProcessingService(
                        context,
                        listingClient
                );
    }


    public List<ListingResponseDto> process() {

        Long botId =
                context.getBot()
                        .getId();


        /*
         * 1. Skanujemy dokładnie to, co jest teraz
         * widoczne w przefiltrowanym katalogu Vinted.
         */
        var scannedListings =
                listingScanner.scan();


        Set<String> currentScanListingIds =
                new HashSet<>();


        for (var scannedListing : scannedListings) {

            if (scannedListing.getId() != null) {

                currentScanListingIds.add(
                        scannedListing.getId()
                );
            }
        }


        log.info(
                "[CURRENT SCAN] Current filtered Vinted scan contains "
                        + "{} unique marketplace listing IDs.",
                currentScanListingIds.size()
        );


        if (currentScanListingIds.isEmpty()) {

            log.warn(
                    "[CURRENT SCAN] Current filtered Vinted scan is empty. "
                            + "No new negotiations will be started."
            );

            return List.of();
        }


        /*
         * 2. Backend zapisuje/claimuje listingi,
         * których jeszcze nie zna.
         */
        var newlyClaimedListings =
                listingProcessingService.process(
                        scannedListings
                );


        log.info(
                "Bot {} claimed {} new listings during this scan",
                botId,
                newlyClaimedListings.size()
        );


        /*
         * 3. Backend może mieć dużo starszych DISCOVERED.
         *
         * Do dalszej pracy dopuszczamy tylko te,
         * które nadal znajdują się w AKTUALNYM skanie.
         */
        List<ListingResponseDto> discoveredListings =
                listingClient.getDiscoveredListings(
                        botId
                );


        List<ListingResponseDto> currentScanDiscoveredListings =
                discoveredListings.stream()
                        .filter(
                                listing ->
                                        listing.listingId() != null
                                                && currentScanListingIds.contains(
                                                listing.listingId()
                                        )
                        )
                        .toList();


        log.info(
                "[CURRENT SCAN] Backend has {} DISCOVERED listings, "
                        + "but only {} belong to the current "
                        + "filtered Vinted scan.",
                discoveredListings.size(),
                currentScanDiscoveredListings.size()
        );


        if (currentScanDiscoveredListings.isEmpty()) {

            log.info(
                    "[CURRENT SCAN] Bot {} has no DISCOVERED listings "
                            + "present in the current filtered Vinted scan.",
                    botId
            );

            return List.of();
        }


        /*
         * 4. TWARDY PRICE GUARD.
         *
         * Filtr Vinted nie jest jedynym zabezpieczeniem.
         * Jeszcze raz sprawdzamy min/max na danych z backendu.
         */
        List<ListingResponseDto> priceEligibleListings =
                new ArrayList<>();


        int skippedOutsidePriceRange =
                0;


        for (
                ListingResponseDto listing
                : currentScanDiscoveredListings
        ) {

            if (
                    isListingWithinConfiguredPriceRange(
                            listing
                    )
            ) {

                priceEligibleListings.add(
                        listing
                );

                continue;
            }


            listingStatusUpdater.markOutsidePriceRange(
                    botId,
                    listing
            );


            skippedOutsidePriceRange++;
        }


        log.info(
                "[PRICE GUARD] Checked {} current-scan DISCOVERED listings. "
                        + "Eligible: {}, skipped outside configured "
                        + "price range: {}.",
                currentScanDiscoveredListings.size(),
                priceEligibleListings.size(),
                skippedOutsidePriceRange
        );


        if (priceEligibleListings.isEmpty()) {

            log.info(
                    "[PRICE GUARD] Bot {} has no current-scan listings "
                            + "inside the configured price range.",
                    botId
            );

            return List.of();
        }


        return priceEligibleListings;
    }


    private boolean isListingWithinConfiguredPriceRange(
            ListingResponseDto listing
    ) {

        BigDecimal listingPrice =
                listing.originalPrice();


        if (
                listingPrice == null
        ) {

            log.warn(
                    "[PRICE GUARD] Backend listing {} has no original price. "
                            + "It will not be negotiated.",
                    listing.id()
            );

            return false;
        }


        BotConfigurationDto configuration =
                context.getBot()
                        .getConfiguration();


        if (
                configuration == null
        ) {

            throw new IllegalStateException(
                    "Bot configuration is missing"
            );
        }


        BigDecimal minPrice =
                configuration.getMinPrice();

        BigDecimal maxPrice =
                configuration.getMaxPrice();


        if (
                minPrice != null
                        && listingPrice.compareTo(
                        minPrice
                ) < 0
        ) {

            log.info(
                    "[PRICE GUARD] Skipping marketplace listing {}. "
                            + "Price {} is below configured minimum {}.",
                    listing.listingId(),
                    listingPrice,
                    minPrice
            );

            return false;
        }


        if (
                maxPrice != null
                        && listingPrice.compareTo(
                        maxPrice
                ) > 0
        ) {

            log.info(
                    "[PRICE GUARD] Skipping marketplace listing {}. "
                            + "Price {} is above configured maximum {}.",
                    listing.listingId(),
                    listingPrice,
                    maxPrice
            );

            return false;
        }


        return true;
    }
}
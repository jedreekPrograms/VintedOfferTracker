package pl.flipbot.playwright.marketstats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.FilterService;
import pl.flipbot.playwright.login.LoginService;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.marketstats.dto.MarketObservationBatchResponseDto;
import pl.flipbot.playwright.marketstats.dto.MarketStatsTargetDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.scanner.ListingScanner;
import pl.flipbot.playwright.scanner.model.Listing;
import pl.flipbot.playwright.target.ListingTargetAssessment;
import pl.flipbot.playwright.target.ListingTargetMatcher;

import java.text.Normalizer;
import java.util.*;

@Slf4j
@RequiredArgsConstructor
public class MarketStatsCollector {

    private static final double SCROLL_WAIT_MS = 1_200;
    private static final int MAX_NO_GROWTH_ROUNDS = 2;

    private static final Set<String> ACCESSORY_WORDS = Set.of(
            "etui",
            "case",
            "cover",
            "pokrowiec",
            "obudowa",
            "szklo",
            "folia",
            "protector",
            "ladowarka",
            "charger",
            "kabel",
            "cable",
            "uchwyt",
            "holder",
            "bateria",
            "battery",
            "wyswietlacz",
            "display",
            "lcd",
            "digitizer",
            "czesci",
            "parts",
            "pudelko",
            "dummy",
            "atrapa"
    );

    private final MarketStatsRuntimeConfig config;
    private final MarketStatsApiClient apiClient;

    public void collectOnce() {
        BotDetailsDto observerBot = apiClient.getObserverBot(
                config.observerBotId()
        );

        List<MarketStatsTargetDto> targets = apiClient.getTargets();

        log.info(
                "[MARKET STATS] Starting daily collection. observerBot={}, targets={}.",
                observerBot.getId(),
                targets.size()
        );

        try (BrowserManager browserManager = new BrowserManager(config.headless());
             BotContext context = new BotContext(observerBot, browserManager)) {

            LoginService loginService = new LoginService(context);
            loginService.login();

            try {
                for (int index = 0; index < targets.size(); index++) {
                    MarketStatsTargetDto target = targets.get(index);

                    try {
                        collectTarget(
                                context,
                                observerBot,
                                target
                        );
                    } catch (Exception exception) {
                        log.error(
                                "[MARKET STATS] Model scan failed. modelId={}, brand='{}', model='{}'. Continuing with the next model.",
                                target.modelId(),
                                target.brandName(),
                                target.modelName(),
                                exception
                        );
                    }

                    if (index + 1 < targets.size()
                            && config.interModelDelayMillis() > 0) {
                        context.getPage().waitForTimeout(
                                config.interModelDelayMillis()
                        );
                    }
                }
            } finally {
                try {
                    context.saveSession();
                } catch (Exception exception) {
                    log.warn(
                            "[MARKET STATS] Could not save observer session after daily scan.",
                            exception
                    );
                }
            }
        }

        log.info("[MARKET STATS] Daily collection finished.");
    }

    private void collectTarget(
            BotContext context,
            BotDetailsDto observerBot,
            MarketStatsTargetDto target
    ) {
        if (target.modelId() == null
                || target.modelId() <= 0
                || isBlank(target.brandName())
                || isBlank(target.modelName())) {
            log.warn(
                    "[MARKET STATS] Skipping invalid target: {}.",
                    target
            );
            return;
        }

        BotDetailsDto scanBot = buildScanBot(
                observerBot,
                target
        );

        Set<String> knownListingIds = apiClient.getKnownListingIds(
                target.modelId()
        );

        if (!target.categoryResolved()) {
            log.warn(
                    "[MARKET STATS] Model {} / {} has no uniquely resolved category from existing bot configurations. "
                            + "Collector will use global brand + text search and conservative accessory filtering.",
                    target.brandName(),
                    target.modelName()
            );
        }

        MarketplaceNavigator navigator = new MarketplaceNavigator(context);
        FilterService filterService = new FilterService(context);

        navigator.goToCatalog();
        filterService.applyFilters(scanBot);

        ScanResult scanResult = scanCatalog(
                context,
                scanBot.getConfiguration(),
                target.categoryResolved(),
                knownListingIds
        );

        MarketObservationBatchResponseDto recorded =
                apiClient.recordObservations(
                        target.modelId(),
                        scanResult.listingIds(),
                        scanResult.complete()
                );

        log.info(
                "[MARKET STATS] Model scan recorded. modelId={}, brand='{}', model='{}', matched={}, knownBefore={}, newObserved={}, complete={}, baseline={}.",
                target.modelId(),
                target.brandName(),
                target.modelName(),
                scanResult.listingIds().size(),
                knownListingIds.size(),
                recorded.newListings(),
                scanResult.complete(),
                recorded.baselineCreated()
        );
    }

    private ScanResult scanCatalog(
            BotContext context,
            BotConfigurationDto targetConfiguration,
            boolean categoryResolved,
            Set<String> knownListingIds
    ) {
        ListingScanner scanner = new ListingScanner(context);
        ListingTargetMatcher matcher = new ListingTargetMatcher();
        LinkedHashMap<String, Listing> matched = new LinkedHashMap<>();

        int previousParsedCount = -1;
        int noGrowthRounds = 0;
        boolean complete = false;

        while (matched.size() < config.maxListingsPerModel()) {
            List<Listing> loaded = scanner.scan();

            if (loaded.isEmpty()) {
                complete = true;
                break;
            }

            for (Listing listing : loaded) {
                if (listing == null || isBlank(listing.getId())) {
                    continue;
                }

                if (matchesTarget(
                        listing,
                        targetConfiguration,
                        categoryResolved,
                        matcher
                )) {
                    matched.putIfAbsent(
                            listing.getId(),
                            listing
                    );
                }
            }

            if (!knownListingIds.isEmpty()
                    && containsKnownBoundary(
                    matched.keySet(),
                    knownListingIds
            )) {
                complete = true;
                break;
            }

            if (loaded.size() <= previousParsedCount) {
                noGrowthRounds++;
            } else {
                noGrowthRounds = 0;
            }

            if (noGrowthRounds >= MAX_NO_GROWTH_ROUNDS) {
                complete = true;
                break;
            }

            previousParsedCount = loaded.size();

            if (matched.size() >= config.maxListingsPerModel()) {
                break;
            }

            context.getPage().evaluate(
                    "window.scrollTo(0, document.body.scrollHeight)"
            );
            context.getPage().waitForTimeout(SCROLL_WAIT_MS);
        }

        List<String> ids = matched.keySet()
                .stream()
                .limit(config.maxListingsPerModel())
                .toList();

        if (ids.size() >= config.maxListingsPerModel()
                && !knownListingIds.isEmpty()
                && !containsKnownBoundary(ids, knownListingIds)) {
            complete = false;
        }

        return new ScanResult(
                ids,
                complete
        );
    }

    private boolean containsKnownBoundary(
            Collection<String> orderedListingIds,
            Set<String> knownListingIds
    ) {
        int consecutiveKnown = 0;

        for (String listingId : orderedListingIds) {
            if (knownListingIds.contains(listingId)) {
                consecutiveKnown++;

                if (consecutiveKnown >= config.knownBoundarySize()) {
                    return true;
                }
            } else {
                consecutiveKnown = 0;
            }
        }

        return false;
    }

    private boolean matchesTarget(
            Listing listing,
            BotConfigurationDto configuration,
            boolean categoryResolved,
            ListingTargetMatcher matcher
    ) {
        if (!categoryResolved && looksLikeAccessory(listing)) {
            return false;
        }

        ListingResponseDto candidate = new ListingResponseDto(
                null,
                listing.getId(),
                listing.getTitle(),
                listing.getUrl(),
                listing.getPrice(),
                listing.getPrice(),
                0,
                false,
                null,
                null,
                "DISCOVERED",
                null
        );

        ListingTargetAssessment catalogAssessment =
                matcher.assessCatalogListing(
                        candidate,
                        configuration
                );

        if (catalogAssessment == ListingTargetAssessment.MATCH) {
            return true;
        }

        if (catalogAssessment == ListingTargetAssessment.MISMATCH) {
            return false;
        }

        return matcher.assessListingUrl(
                candidate,
                configuration
        ) == ListingTargetAssessment.MATCH;
    }

    private boolean looksLikeAccessory(
            Listing listing
    ) {
        String text = normalize(
                String.valueOf(listing.getTitle())
                        + " "
                        + String.valueOf(listing.getUrl())
        );

        for (String accessoryWord : ACCESSORY_WORDS) {
            if (text.contains(accessoryWord)) {
                return true;
            }
        }

        return false;
    }

    private BotDetailsDto buildScanBot(
            BotDetailsDto observerBot,
            MarketStatsTargetDto target
    ) {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setMarketplace("VINTED");
        configuration.setCategoryPath(
                target.categoryResolved()
                        && target.categoryPath() != null
                        ? List.copyOf(target.categoryPath())
                        : List.of()
        );
        configuration.setBrand(target.brandName());

        /*
         * Market statistics intentionally use text search even when the
         * production bot uses a strict Vinted model filter. This keeps the
         * collector independent from whether Vinted currently exposes the
         * model in its filter UI. The existing strict title/URL matcher is
         * still applied before an ID is counted.
         */
        configuration.setTargetMode("SEARCH_QUERY");
        configuration.setModel(null);
        configuration.setSearchQuery(target.modelName());
        configuration.setMinPrice(null);
        configuration.setMaxPrice(null);

        BotDetailsDto scanBot = new BotDetailsDto();
        scanBot.setId(observerBot.getId());
        scanBot.setName("Market stats observer");
        scanBot.setEmail(observerBot.getEmail());
        scanBot.setPassword(observerBot.getPassword());
        scanBot.setConfiguration(configuration);

        return scanBot;
    }

    private boolean isBlank(
            String value
    ) {
        return value == null || value.isBlank();
    }

    private String normalize(
            String value
    ) {
        String normalized = Normalizer.normalize(
                value == null ? "" : value,
                Normalizer.Form.NFD
        );

        return normalized
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT);
    }

    private record ScanResult(
            List<String> listingIds,
            boolean complete
    ) {
    }
}

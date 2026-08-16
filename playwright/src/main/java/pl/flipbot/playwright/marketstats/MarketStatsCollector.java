package pl.flipbot.playwright.marketstats;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.FilterService;
import pl.flipbot.playwright.login.LoginService;
import pl.flipbot.playwright.marketplace.MarketplaceNavigator;
import pl.flipbot.playwright.marketstats.dto.KnownMarketListingIdsDto;
import pl.flipbot.playwright.marketstats.dto.MarketObservationBatchResponseDto;
import pl.flipbot.playwright.marketstats.dto.MarketStatsTargetDto;
import pl.flipbot.playwright.model.BotConfigurationDto;
import pl.flipbot.playwright.model.BotDetailsDto;
import pl.flipbot.playwright.scanner.ListingScanner;
import pl.flipbot.playwright.scanner.model.Listing;
import pl.flipbot.playwright.target.ListingTargetAssessment;
import pl.flipbot.playwright.target.ListingTargetMatcher;

import java.text.Normalizer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class MarketStatsCollector {

    private static final String VINTED_MODEL = "VINTED_MODEL";
    private static final String SEARCH_QUERY = "SEARCH_QUERY";

    private static final String STRATEGY_DICTIONARY_FILTERS =
            "DICTIONARY_FILTERS";
    private static final String STRATEGY_GLOBAL_BRAND_TEXT =
            "GLOBAL_BRAND_TEXT_FALLBACK";
    private static final String STRATEGY_CATEGORY_TEXT =
            "CATEGORY_TEXT_FALLBACK";
    private static final String STRATEGY_TEXT_ONLY =
            "TEXT_ONLY_FALLBACK";

    private static final double SCROLL_WAIT_MS = 1_200;
    private static final int MAX_NO_GROWTH_ROUNDS = 2;

    /*
     * Used for broad discovery fallbacks. The strict target matcher remains
     * the final authority before a marketplace listing id is counted.
     */
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
            "digitizer",
            "czesci",
            "parts",
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

        int recordedTargets = 0;
        int fallbackTargets = 0;
        int failedTargets = 0;

        try (BrowserManager browserManager = new BrowserManager(config.headless());
             BotContext context = new BotContext(observerBot, browserManager)) {

            LoginService loginService = new LoginService(context);
            loginService.login();

            try {
                for (int index = 0; index < targets.size(); index++) {
                    MarketStatsTargetDto target = targets.get(index);

                    try {
                        boolean fallbackUsed = collectTarget(
                                context,
                                observerBot,
                                target
                        );

                        recordedTargets++;

                        if (fallbackUsed) {
                            fallbackTargets++;
                        }
                    } catch (Exception exception) {
                        failedTargets++;

                        log.error(
                                "[MARKET STATS] Model scan failed. modelId={}, brand='{}', model='{}'. "
                                        + "Continuing with the next model; the whole collection will be retried sooner.",
                                target == null ? null : target.modelId(),
                                target == null ? null : target.brandName(),
                                target == null ? null : target.modelName(),
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

        log.info(
                "[MARKET STATS] Daily collection pass finished. targets={}, recorded={}, "
                        + "fallback={}, failed={}.",
                targets.size(),
                recordedTargets,
                fallbackTargets,
                failedTargets
        );

        if (failedTargets > 0) {
            throw new IllegalStateException(
                    "Market statistics collection was incomplete: "
                            + failedTargets
                            + " of "
                            + targets.size()
                            + " model scans failed."
            );
        }

        log.info("[MARKET STATS] Daily collection finished successfully for all targets.");
    }

    private boolean collectTarget(
            BotContext context,
            BotDetailsDto observerBot,
            MarketStatsTargetDto target
    ) {
        validateTarget(target);

        KnownMarketListingIdsDto knownState =
                apiClient.getKnownListingIds(
                        target.modelId()
                );

        Set<String> knownListingIds =
                knownState.listingIds() == null
                        ? Set.of()
                        : Set.copyOf(knownState.listingIds());

        PreparedScan preparedScan = prepareScan(
                context,
                observerBot,
                target
        );

        ScanResult scanResult = scanCatalog(
                context,
                preparedScan.scanBot().getConfiguration(),
                preparedScan.accessoryFiltering(),
                knownListingIds,
                knownState.baselineComplete()
        );

        MarketObservationBatchResponseDto recorded =
                apiClient.recordObservations(
                        target.modelId(),
                        scanResult.listingIds(),
                        scanResult.complete()
                );

        log.info(
                "[MARKET STATS] Model scan recorded. modelId={}, brand='{}', model='{}', "
                        + "strategy={}, minPrice={}, maxPrice={}, matched={}, knownBefore={}, "
                        + "newObserved={}, complete={}, baselineMode={}.",
                target.modelId(),
                target.brandName(),
                target.modelName(),
                preparedScan.strategy(),
                target.minPrice(),
                target.maxPrice(),
                scanResult.listingIds().size(),
                knownListingIds.size(),
                recorded.newListings(),
                scanResult.complete(),
                !knownState.baselineComplete()
        );

        return !STRATEGY_DICTIONARY_FILTERS.equals(
                preparedScan.strategy()
        );
    }

    private PreparedScan prepareScan(
            BotContext context,
            BotDetailsDto observerBot,
            MarketStatsTargetDto target
    ) {
        boolean resolvedCategory = hasResolvedCategory(target);

        BotDetailsDto primaryBot = buildScanBot(
                observerBot,
                target
        );

        String primaryStrategy = resolvedCategory
                ? STRATEGY_DICTIONARY_FILTERS
                : STRATEGY_GLOBAL_BRAND_TEXT;

        if (!resolvedCategory) {
            log.warn(
                    "[MARKET STATS] modelId={} {} / {} has no resolved category. "
                            + "Collector will NOT skip it: trying brand + text search without a category first.",
                    target.modelId(),
                    target.brandName(),
                    target.modelName()
            );
        }

        try {
            applyTargetFilters(
                    context,
                    target,
                    primaryBot,
                    primaryStrategy
            );

            return new PreparedScan(
                    primaryBot,
                    primaryStrategy,
                    !STRATEGY_DICTIONARY_FILTERS.equals(primaryStrategy)
            );
        } catch (RuntimeException primaryFailure) {
            if (containsInterruptedException(primaryFailure)) {
                throw primaryFailure;
            }

            log.warn(
                    "[MARKET STATS] Primary filter strategy failed for modelId={} {} / {}. "
                            + "strategy={}, reason={}",
                    target.modelId(),
                    target.brandName(),
                    target.modelName(),
                    primaryStrategy,
                    safeMessage(primaryFailure)
            );

            if (resolvedCategory
                    && VINTED_MODEL.equals(resolveTargetMode(target.targetMode()))) {
                BotDetailsDto categoryTextBot = buildCategoryTextFallbackBot(
                        observerBot,
                        target
                );

                try {
                    applyTargetFilters(
                            context,
                            target,
                            categoryTextBot,
                            STRATEGY_CATEGORY_TEXT
                    );

                    return new PreparedScan(
                            categoryTextBot,
                            STRATEGY_CATEGORY_TEXT,
                            true
                    );
                } catch (RuntimeException categoryTextFailure) {
                    if (containsInterruptedException(categoryTextFailure)) {
                        throw categoryTextFailure;
                    }

                    log.warn(
                            "[MARKET STATS] Category text fallback also failed for modelId={} {} / {}. "
                                    + "Falling back to text-only discovery. reason={}",
                            target.modelId(),
                            target.brandName(),
                            target.modelName(),
                            safeMessage(categoryTextFailure)
                    );
                }
            }

            BotDetailsDto textOnlyBot = buildTextOnlyFallbackBot(
                    observerBot,
                    target
            );

            applyTargetFilters(
                    context,
                    target,
                    textOnlyBot,
                    STRATEGY_TEXT_ONLY
            );

            return new PreparedScan(
                    textOnlyBot,
                    STRATEGY_TEXT_ONLY,
                    true
            );
        }
    }

    private void applyTargetFilters(
            BotContext context,
            MarketStatsTargetDto target,
            BotDetailsDto scanBot,
            String strategy
    ) {
        MarketplaceNavigator navigator = new MarketplaceNavigator(context);
        FilterService filterService = new FilterService(context);

        log.info(
                "[MARKET STATS] Applying target. modelId={}, strategy={}, categoryPath={}, brand='{}', "
                        + "targetMode={}, model='{}', searchQuery='{}', minPrice={}, maxPrice={}.",
                target.modelId(),
                strategy,
                scanBot.getConfiguration().getCategoryPath(),
                scanBot.getConfiguration().getBrand(),
                scanBot.getConfiguration().getTargetMode(),
                scanBot.getConfiguration().getModel(),
                scanBot.getConfiguration().getSearchQuery(),
                scanBot.getConfiguration().getMinPrice(),
                scanBot.getConfiguration().getMaxPrice()
        );

        navigator.goToCatalog();
        filterService.applyFilters(scanBot);
    }

    private ScanResult scanCatalog(
            BotContext context,
            BotConfigurationDto targetConfiguration,
            boolean accessoryFiltering,
            Set<String> knownListingIds,
            boolean baselineComplete
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
                        accessoryFiltering,
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

        boolean hitLimit =
                ids.size() >= config.maxListingsPerModel();

        if (hitLimit) {
            if (!baselineComplete) {
                complete = true;

                log.info(
                        "[MARKET STATS] Baseline seed reached configured limit {}. "
                                + "Treating the bounded newest-first seed as a complete baseline.",
                        config.maxListingsPerModel()
                );

            } else if (!containsKnownBoundary(ids, knownListingIds)) {
                complete = false;

                log.warn(
                        "[MARKET STATS] Daily scan reached configured limit {} before the known-listing boundary. "
                                + "This scan is incomplete.",
                        config.maxListingsPerModel()
                );
            }
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
            boolean accessoryFiltering,
            ListingTargetMatcher matcher
    ) {
        if (accessoryFiltering && looksLikeAccessory(listing)) {
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
        String normalized = normalizeForAccessoryCheck(
                String.valueOf(listing.getTitle())
                        + " "
                        + String.valueOf(listing.getUrl())
        );

        if (normalized.isBlank()) {
            return false;
        }

        String padded = " " + normalized + " ";

        for (String accessoryWord : ACCESSORY_WORDS) {
            if (padded.contains(" " + accessoryWord + " ")) {
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

        boolean resolvedCategory = hasResolvedCategory(target);

        configuration.setCategoryPath(
                resolvedCategory
                        ? List.copyOf(target.categoryPath())
                        : List.of()
        );

        configuration.setBrand(target.brandName());

        if (!resolvedCategory) {
            configuration.setTargetMode(SEARCH_QUERY);
            configuration.setModel(null);
            configuration.setSearchQuery(target.modelName());
        } else {
            String targetMode = resolveTargetMode(target.targetMode());
            configuration.setTargetMode(targetMode);

            if (SEARCH_QUERY.equals(targetMode)) {
                configuration.setModel(null);
                configuration.setSearchQuery(target.modelName());
            } else {
                configuration.setModel(target.modelName());
                configuration.setSearchQuery(null);
            }
        }

        applyObserverPriceRange(configuration, target);

        return buildBotWithConfiguration(
                observerBot,
                configuration
        );
    }

    private BotDetailsDto buildCategoryTextFallbackBot(
            BotDetailsDto observerBot,
            MarketStatsTargetDto target
    ) {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setMarketplace("VINTED");
        configuration.setCategoryPath(
                List.copyOf(target.categoryPath())
        );
        configuration.setBrand(target.brandName());
        configuration.setTargetMode(SEARCH_QUERY);
        configuration.setModel(null);
        configuration.setSearchQuery(target.modelName());
        applyObserverPriceRange(configuration, target);

        return buildBotWithConfiguration(
                observerBot,
                configuration
        );
    }

    private BotDetailsDto buildTextOnlyFallbackBot(
            BotDetailsDto observerBot,
            MarketStatsTargetDto target
    ) {
        BotConfigurationDto configuration = new BotConfigurationDto();
        configuration.setMarketplace("VINTED");
        configuration.setCategoryPath(List.of());
        configuration.setBrand(null);
        configuration.setTargetMode(SEARCH_QUERY);
        configuration.setModel(null);
        configuration.setSearchQuery(
                target.brandName().trim()
                        + " "
                        + target.modelName().trim()
        );
        applyObserverPriceRange(configuration, target);

        return buildBotWithConfiguration(
                observerBot,
                configuration
        );
    }

    private void applyObserverPriceRange(
            BotConfigurationDto configuration,
            MarketStatsTargetDto target
    ) {
        configuration.setMinPrice(target.minPrice());
        configuration.setMaxPrice(target.maxPrice());
    }

    private BotDetailsDto buildBotWithConfiguration(
            BotDetailsDto observerBot,
            BotConfigurationDto configuration
    ) {
        BotDetailsDto scanBot = new BotDetailsDto();
        scanBot.setId(observerBot.getId());
        scanBot.setName("Market stats observer");
        scanBot.setEmail(observerBot.getEmail());
        scanBot.setPassword(observerBot.getPassword());
        scanBot.setConfiguration(configuration);

        return scanBot;
    }

    private void validateTarget(
            MarketStatsTargetDto target
    ) {
        if (target == null
                || target.modelId() == null
                || target.modelId() <= 0
                || isBlank(target.brandName())
                || isBlank(target.modelName())) {
            throw new IllegalArgumentException(
                    "Invalid market statistics target: " + target
            );
        }

        if (target.minPrice() != null
                && target.minPrice().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Market statistics minimum price must be positive: " + target
            );
        }

        if (target.maxPrice() != null
                && target.maxPrice().signum() <= 0) {
            throw new IllegalArgumentException(
                    "Market statistics maximum price must be positive: " + target
            );
        }

        if (target.minPrice() != null
                && target.maxPrice() != null
                && target.minPrice().compareTo(target.maxPrice()) > 0) {
            throw new IllegalArgumentException(
                    "Market statistics minimum price cannot exceed maximum price: " + target
            );
        }
    }

    private boolean hasResolvedCategory(
            MarketStatsTargetDto target
    ) {
        return target.categoryResolved()
                && target.categoryPath() != null
                && !target.categoryPath().isEmpty();
    }

    private String resolveTargetMode(
            String rawTargetMode
    ) {
        if (SEARCH_QUERY.equals(rawTargetMode)) {
            return SEARCH_QUERY;
        }

        return VINTED_MODEL;
    }

    private boolean containsInterruptedException(
            Throwable throwable
    ) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private String safeMessage(
            Throwable throwable
    ) {
        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return throwable == null
                    ? "unknown error"
                    : throwable.getClass().getSimpleName();
        }

        return throwable.getMessage();
    }

    private boolean isBlank(
            String value
    ) {
        return value == null || value.isBlank();
    }

    private String normalizeForAccessoryCheck(
            String value
    ) {
        String normalized = Normalizer.normalize(
                value == null ? "" : value,
                Normalizer.Form.NFD
        );

        return normalized
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private record PreparedScan(
            BotDetailsDto scanBot,
            String strategy,
            boolean accessoryFiltering
    ) {
    }

    private record ScanResult(
            List<String> listingIds,
            boolean complete
    ) {
    }
}

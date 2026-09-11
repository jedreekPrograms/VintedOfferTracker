package pl.flipbot.playwright.marketstats;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.api.listing.dto.ListingResponseDto;
import pl.flipbot.playwright.browser.BrowserManager;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.filters.FilterService;
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
import java.util.ArrayList;
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
    private static final String STRATEGY_TEXT_ONLY =
            "TEXT_ONLY_FALLBACK";

    private static final double PAGE_WAIT_MS = 1_000;
    private static final double CATALOG_PAGE_NAVIGATION_TIMEOUT_MS = 30_000;
    private static final int MAX_NO_GROWTH_PAGES = 2;

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

            boolean authenticatedObserverSession =
                    prepareObserverCatalogSession(context, observerBot);

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
                if (authenticatedObserverSession) {
                    try {
                        context.saveSession();
                    } catch (Exception exception) {
                        log.warn(
                                "[MARKET STATS] Could not save observer session after daily scan.",
                                exception
                        );
                    }
                } else {
                    log.info(
                            "[MARKET STATS] Observer collection ran without an authenticated session. "
                                    + "Skipping session save so anonymous storage state cannot replace the saved observer session."
                    );
                }
            }
        }

        log.info(
                "[MARKET STATS] Daily collection pass finished. targets={}, recorded={}, fallback={}, failed={}.",
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

    private boolean prepareObserverCatalogSession(
            BotContext context,
            BotDetailsDto observerBot
    ) {
        MarketplaceNavigator navigator = new MarketplaceNavigator(context);
        navigator.goToCatalog();

        /*
         * MarketplaceNavigator already waits for DOMContentLoaded and probes
         * the catalog shell. Waiting for the browser LOAD state again is both
         * redundant and unsafe on Vinted because ad/analytics resources can
         * keep the document loading long after the catalog is usable.
         */
        dismissCookieBannerIfVisible(context);

        boolean authenticated =
                hasVisible(
                        context,
                        "[data-testid='header-conversations-button']"
                )
                        || hasVisible(context, "a[href*='/inbox']");

        if (authenticated) {
            log.info(
                    "[MARKET STATS] Observer bot {} restored an authenticated Vinted session. "
                            + "Using it for this read-only collection; no interactive login is needed.",
                    observerBot.getId()
            );
        } else {
            log.info(
                    "[MARKET STATS] Observer bot {} has no verifiably authenticated stored Vinted session. "
                            + "Continuing immediately with anonymous READ-ONLY catalog collection; "
                            + "interactive login is intentionally skipped and anonymous state will not be saved.",
                    observerBot.getId()
            );
        }

        return authenticated;
    }

    private void dismissCookieBannerIfVisible(BotContext context) {
        try {
            var button = context.getPage().locator("#onetrust-accept-btn-handler");

            if (button.count() > 0 && button.first().isVisible()) {
                button.first().click();
                log.debug(
                        "[MARKET STATS] Accepted Vinted cookie banner before observer scan."
                );
            }
        } catch (RuntimeException exception) {
            log.debug(
                    "[MARKET STATS] Cookie banner was not actionable; catalog scan will continue.",
                    exception
            );
        }
    }

    private boolean hasVisible(
            BotContext context,
            String selector
    ) {
        try {
            var locator = context.getPage().locator(selector);
            int count = locator.count();

            for (int index = 0; index < count; index++) {
                if (locator.nth(index).isVisible()) {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            log.debug(
                    "[MARKET STATS] Could not probe observer authentication selector '{}'.",
                    selector,
                    exception
            );
        }

        return false;
    }

    private boolean collectTarget(
            BotContext context,
            BotDetailsDto observerBot,
            MarketStatsTargetDto target
    ) {
        validateTarget(target);

        try {
            KnownMarketListingIdsDto knownState =
                    apiClient.getKnownListingIds(target.modelId());

            Set<String> knownListingIds = knownState.listingIds() == null
                    ? Set.of()
                    : Set.copyOf(knownState.listingIds());

            PreparedScan preparedScan = prepareScan(
                    context,
                    observerBot,
                    target
            );

            boolean trustVintedModelFilter =
                    VINTED_MODEL.equals(resolveTargetMode(target.targetMode()))
                            && STRATEGY_DICTIONARY_FILTERS.equals(
                                    preparedScan.strategy()
                            );

            ScanResult scanResult = scanCatalog(
                    context,
                    preparedScan.scanBot().getConfiguration(),
                    preparedScan.accessoryFiltering(),
                    trustVintedModelFilter,
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
                            + "strategy={}, trustedVintedFilter={}, minPrice={}, maxPrice={}, matched={}, knownBefore={}, "
                            + "newObserved={}, complete={}, baselineMode={}.",
                    target.modelId(),
                    target.brandName(),
                    target.modelName(),
                    preparedScan.strategy(),
                    trustVintedModelFilter,
                    target.minPrice(),
                    target.maxPrice(),
                    scanResult.listingIds().size(),
                    knownListingIds.size(),
                    recorded.newListings(),
                    recorded.complete(),
                    !knownState.baselineComplete()
            );

            return !STRATEGY_DICTIONARY_FILTERS.equals(
                    preparedScan.strategy()
            );
        } catch (RuntimeException exception) {
            apiClient.clearObservationContext(target.modelId());
            throw exception;
        }
    }

    private PreparedScan prepareScan(
            BotContext context,
            BotDetailsDto observerBot,
            MarketStatsTargetDto target
    ) {
        boolean resolvedCategory = hasResolvedCategory(target);
        String requestedTargetMode = resolveTargetMode(target.targetMode());

        if (VINTED_MODEL.equals(requestedTargetMode) && !resolvedCategory) {
            throw new IllegalStateException(
                    "VINTED_MODEL market target "
                            + target.brandName()
                            + " / "
                            + target.modelName()
                            + " has no resolved category. Exact Vinted model filtering is mandatory; refusing SEARCH_QUERY fallback."
            );
        }

        BotDetailsDto primaryBot = buildScanBot(observerBot, target);
        String primaryStrategy = resolvedCategory
                ? STRATEGY_DICTIONARY_FILTERS
                : STRATEGY_GLOBAL_BRAND_TEXT;

        if (!resolvedCategory) {
            log.warn(
                    "[MARKET STATS] modelId={} {} / {} has no resolved category. "
                            + "SEARCH_QUERY collector will try brand + text search without a category first.",
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
                    SEARCH_QUERY.equals(requestedTargetMode)
            );
        } catch (RuntimeException primaryFailure) {
            if (containsInterruptedException(primaryFailure)) {
                throw primaryFailure;
            }

            log.warn(
                    "[MARKET STATS] Primary filter strategy failed for modelId={} {} / {}. "
                            + "strategy={}, targetMode={}, reason={}",
                    target.modelId(),
                    target.brandName(),
                    target.modelName(),
                    primaryStrategy,
                    requestedTargetMode,
                    safeMessage(primaryFailure)
            );

            if (VINTED_MODEL.equals(requestedTargetMode)) {
                log.error(
                        "[MARKET STATS] Exact Vinted model filter is mandatory for modelId={} {} / {}. "
                                + "This target will FAIL CLOSED; no category-text or text-only fallback is allowed.",
                        target.modelId(),
                        target.brandName(),
                        target.modelName()
                );

                throw new IllegalStateException(
                        "Exact Vinted model filtering failed for "
                                + target.brandName()
                                + " / "
                                + target.modelName()
                                + "; refusing to collect statistics from a different search strategy.",
                        primaryFailure
                );
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
            boolean trustVintedModelFilter,
            Set<String> knownListingIds,
            boolean baselineComplete
    ) {
        ListingScanner scanner = new ListingScanner(context);
        ListingTargetMatcher matcher = new ListingTargetMatcher();
        MarketListingPublishedAtResolver publishedAtResolver =
                new MarketListingPublishedAtResolver(context);
        LinkedHashMap<String, Listing> matched = new LinkedHashMap<>();

        String filteredCatalogUrl = context.getPage().url();
        int pageNumber = 1;
        int noGrowthPages = 0;
        boolean complete = false;

        while (matched.size() < config.maxListingsPerModel()) {
            if (pageNumber > 1) {
                if (!navigateToCatalogPage(
                        context,
                        filteredCatalogUrl,
                        pageNumber
                )) {
                    complete = false;
                    break;
                }
            }

            List<Listing> loaded = scanner.scan();

            if (loaded.isEmpty()) {
                complete = true;
                break;
            }

            int beforePage = matched.size();
            List<Listing> newlyAccepted = new ArrayList<>();

            for (Listing listing : loaded) {
                if (listing == null || isBlank(listing.getId())) {
                    continue;
                }

                boolean accepted = trustVintedModelFilter
                        || matchesTarget(
                                listing,
                                targetConfiguration,
                                accessoryFiltering,
                                matcher
                        );

                if (!accepted) {
                    continue;
                }

                Listing previous = matched.putIfAbsent(
                        listing.getId(),
                        listing
                );

                if (previous == null) {
                    newlyAccepted.add(listing);
                }
            }

            publishedAtResolver.captureIfNeeded(newlyAccepted);

            log.info(
                    "[MARKET STATS] Catalog page {} inspected. loaded={}, acceptedNew={}, acceptedTotal={}.",
                    pageNumber,
                    loaded.size(),
                    newlyAccepted.size(),
                    matched.size()
            );

            if (!knownListingIds.isEmpty()
                    && containsKnownBoundary(
                            matched.keySet(),
                            knownListingIds
                    )) {
                complete = true;
                break;
            }

            if (matched.size() == beforePage) {
                noGrowthPages++;
            } else {
                noGrowthPages = 0;
            }

            if (noGrowthPages >= MAX_NO_GROWTH_PAGES) {
                complete = true;
                break;
            }

            if (matched.size() >= config.maxListingsPerModel()) {
                break;
            }

            pageNumber++;
        }

        List<String> ids = matched.keySet()
                .stream()
                .limit(config.maxListingsPerModel())
                .toList();

        boolean hitLimit = ids.size() >= config.maxListingsPerModel();

        if (hitLimit && !containsKnownBoundary(ids, knownListingIds)) {
            complete = false;

            log.warn(
                    "[MARKET STATS] Catalog scan reached configured limit {} before proving the end/known boundary. "
                            + "The scan stays incomplete rather than pretending the partial catalog is a full model window.",
                    config.maxListingsPerModel()
            );
        }

        if (!baselineComplete && complete) {
            log.info(
                    "[MARKET STATS] Initial baseline traversed the available filtered catalog. listings={}.",
                    ids.size()
            );
        }

        return new ScanResult(ids, complete);
    }

    private boolean navigateToCatalogPage(
            BotContext context,
            String filteredCatalogUrl,
            int pageNumber
    ) {
        if (isBlank(filteredCatalogUrl) || pageNumber <= 1) {
            return pageNumber <= 1;
        }

        String nextUrl;

        if (filteredCatalogUrl.matches(".*[?&]page=\\d+.*")) {
            nextUrl = filteredCatalogUrl.replaceFirst(
                    "([?&])page=\\d+",
                    "$1page=" + pageNumber
            );
        } else {
            nextUrl = filteredCatalogUrl
                    + (filteredCatalogUrl.contains("?") ? "&" : "?")
                    + "page="
                    + pageNumber;
        }

        try {
            context.getPage().navigate(
                    nextUrl,
                    catalogPageNavigateOptions()
            );
            context.getPage().waitForTimeout(PAGE_WAIT_MS);

            log.debug(
                    "[MARKET STATS] Opened filtered catalog page {} after DOMContentLoaded. url={}",
                    pageNumber,
                    context.getPage().url()
            );

            return true;
        } catch (RuntimeException exception) {
            log.warn(
                    "[MARKET STATS] Could not open filtered catalog page {}. url={}, reason={}",
                    pageNumber,
                    nextUrl,
                    safeMessage(exception)
            );
            return false;
        }
    }

    static Page.NavigateOptions catalogPageNavigateOptions() {
        return new Page.NavigateOptions()
                .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                .setTimeout(CATALOG_PAGE_NAVIGATION_TIMEOUT_MS);
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

    private boolean looksLikeAccessory(Listing listing) {
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
        return buildBotWithConfiguration(observerBot, configuration);
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

        return buildBotWithConfiguration(observerBot, configuration);
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

    private void validateTarget(MarketStatsTargetDto target) {
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

    private boolean hasResolvedCategory(MarketStatsTargetDto target) {
        return target.categoryResolved()
                && target.categoryPath() != null
                && !target.categoryPath().isEmpty();
    }

    private String resolveTargetMode(String rawTargetMode) {
        return SEARCH_QUERY.equals(rawTargetMode)
                ? SEARCH_QUERY
                : VINTED_MODEL;
    }

    private boolean containsInterruptedException(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof InterruptedException) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().isBlank()) {
            return throwable == null
                    ? "unknown error"
                    : throwable.getClass().getSimpleName();
        }

        return throwable.getMessage();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeForAccessoryCheck(String value) {
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

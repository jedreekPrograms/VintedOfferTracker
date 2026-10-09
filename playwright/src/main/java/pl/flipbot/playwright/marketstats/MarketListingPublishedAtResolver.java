package pl.flipbot.playwright.marketstats;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.marketplace.MarketplaceUrls;
import pl.flipbot.playwright.scanner.model.Listing;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class MarketListingPublishedAtResolver {

    static final String TRAFFIC_BACKOFF_MARKER =
            "MARKET_STATS_TRAFFIC_BACKOFF";

    private static final String ANONYMOUS_MARKET_OBSERVER_NAME =
            "Anonymous Market Observer";
    private static final ZoneId MARKET_ZONE = ZoneId.of("Europe/Warsaw");

    private static final double DETAIL_NAVIGATION_TIMEOUT_MS = 30_000;
    private static final double DETAIL_PUBLICATION_WAIT_MS = 6_000;
    private static final double DETAIL_PUBLICATION_POLL_MS = 250;
    private static final double DETAIL_INTER_NAVIGATION_DELAY_MS = 1_500;
    private static final double SESSION_REFRESH_TIMEOUT_MS = 15_000;
    private static final double SESSION_REFRESH_POLL_MS = 250;

    private final BotContext context;

    public MarketListingPublishedAtResolver(BotContext context) {
        this.context = context;
    }

    public void captureIfNeeded(List<Listing> listings) {
        if (!isAnonymousMarketObserver()
                || listings == null
                || listings.isEmpty()) {
            return;
        }

        List<Listing> detailCandidates = detailCandidates(listings);

        if (detailCandidates.isEmpty()) {
            return;
        }

        Page page = context.getPage();
        String catalogUrl = page.url();

        if (!MarketplaceUrls.isCatalogUrl(catalogUrl)) {
            throw new IllegalStateException(
                    "Market publication resolver expected to start from the filtered Vinted catalog, but current URL is "
                            + catalogUrl
            );
        }

        int resolvedCount = 0;
        List<String> unresolvedIds = new ArrayList<>();
        boolean trafficBackoff = false;

        try {
            for (int index = 0; index < detailCandidates.size(); index++) {
                Listing listing = detailCandidates.get(index);
                String listingId = listing.getId().trim();
                String detailUrl = resolveTrustedListingUrl(listing.getUrl());

                if (index > 0) {
                    page.waitForTimeout(
                            DETAIL_INTER_NAVIGATION_DELAY_MS
                    );
                }

                log.info(
                        "[MARKET STATS] Opening listing detail {}/{} to read Vinted publication time. listingId={}, url={}",
                        index + 1,
                        detailCandidates.size(),
                        listingId,
                        detailUrl
                );

                try {
                    navigateToDetail(page, detailUrl, listingId);
                    String rawPublishedAt =
                            waitForPublishedAt(page, listingId);

                    if (rawPublishedAt == null
                            || rawPublishedAt.isBlank()) {
                        unresolvedIds.add(listingId);
                        log.warn(
                                "[MARKET STATS] Listing detail loaded but publication time was not readable. listingId={}, url={}",
                                listingId,
                                page.url()
                        );
                        continue;
                    }

                    LocalDateTime observedAt =
                            LocalDateTime.now(MARKET_ZONE);

                    VintedPublishedAtParser.parse(
                                    rawPublishedAt,
                                    observedAt
                            )
                            .ifPresentOrElse(
                                    publishedAt -> {
                                        MarketStatsObservationContext
                                                .recordPublishedAt(
                                                        listingId,
                                                        publishedAt
                                                );

                                        log.info(
                                                "[MARKET STATS] Listing publication time resolved from its detail page. listingId={}, publishedAt={}, source='{}'.",
                                                listingId,
                                                publishedAt,
                                                rawPublishedAt
                                        );
                                    },
                                    () -> {
                                        unresolvedIds.add(listingId);
                                        log.warn(
                                                "[MARKET STATS] Vinted publication label could not be parsed. listingId={}, source='{}'.",
                                                listingId,
                                                rawPublishedAt
                                        );
                                    }
                            );

                    if (!unresolvedIds.contains(listingId)) {
                        resolvedCount++;
                    }
                } catch (RuntimeException exception) {
                    if (containsTrafficBackoffMarker(exception)) {
                        trafficBackoff = true;
                        throw exception;
                    }

                    unresolvedIds.add(listingId);
                    log.warn(
                            "[MARKET STATS] Could not inspect listing detail for publication time. listingId={}, url={}, reason={}",
                            listingId,
                            detailUrl,
                            safeMessage(exception)
                    );
                }
            }
        } finally {
            if (!trafficBackoff) {
                restoreCatalog(page, catalogUrl);
            }
        }

        log.info(
                "[MARKET STATS] Sequential detail-page publication scan resolved {}/{} required listing timestamps. Each required listing was opened in the observer browser; no background item fetch shortcut was used.",
                resolvedCount,
                detailCandidates.size()
        );

        if (!unresolvedIds.isEmpty()) {
            log.warn(
                    "[MARKET STATS] Publication timestamp is still unresolved for {}/{} required listing detail pages. sampleIds={}",
                    unresolvedIds.size(),
                    detailCandidates.size(),
                    unresolvedIds.stream().limit(8).toList()
            );
        }
    }

    static List<Listing> detailCandidates(List<Listing> listings) {
        List<Listing> candidates = new ArrayList<>();

        if (listings == null || listings.isEmpty()) {
            return candidates;
        }

        for (Listing listing : listings) {
            if (listing == null
                    || listing.getId() == null
                    || listing.getId().isBlank()
                    || listing.getUrl() == null
                    || listing.getUrl().isBlank()) {
                continue;
            }

            String listingId = listing.getId().trim();

            MarketStatsObservationContext.recordObservedListingId(
                    listingId
            );

            if (MarketStatsObservationContext.claimPublicationResolution(
                    listingId
            )) {
                candidates.add(listing);
            }
        }

        return candidates;
    }

    private void navigateToDetail(
            Page page,
            String detailUrl,
            String listingId
    ) {
        Response response = page.navigate(
                detailUrl,
                new Page.NavigateOptions()
                        .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                        .setTimeout(DETAIL_NAVIGATION_TIMEOUT_MS)
        );

        throwIfRateLimitedResponse(response, "listing detail", listingId);
        waitForSessionRefreshResolution(page, detailUrl);

        if (!isExpectedListingUrl(page.url(), listingId)) {
            throw new IllegalStateException(
                    "Vinted listing navigation did not finish on the expected item. listingId="
                            + listingId
                            + ", currentUrl="
                            + page.url()
            );
        }

        throwIfBlockedPage(page, listingId);
    }

    private String waitForPublishedAt(
            Page page,
            String listingId
    ) {
        long deadline =
                System.currentTimeMillis()
                        + (long) DETAIL_PUBLICATION_WAIT_MS;

        while (System.currentTimeMillis() <= deadline) {
            throwIfBlockedPage(page, listingId);

            Object raw = page.evaluate(
                    MarketPublicationDomScripts.EXTRACT_PUBLISHED_AT_SCRIPT,
                    listingId
            );

            if (raw instanceof String value && !value.isBlank()) {
                return value.trim();
            }

            page.waitForTimeout(DETAIL_PUBLICATION_POLL_MS);
        }

        return null;
    }

    private void restoreCatalog(
            Page page,
            String catalogUrl
    ) {
        try {
            Response response = page.navigate(
                    catalogUrl,
                    new Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(DETAIL_NAVIGATION_TIMEOUT_MS)
            );

            throwIfRateLimitedResponse(
                    response,
                    "filtered catalog restore",
                    null
            );
            waitForSessionRefreshResolution(page, catalogUrl);

            if (!MarketplaceUrls.isCatalogUrl(page.url())) {
                throw new IllegalStateException(
                        "Market observer could not restore its filtered catalog after reading listing details. Current URL: "
                                + page.url()
                );
            }

            page.waitForTimeout(500);
        } catch (RuntimeException exception) {
            if (containsTrafficBackoffMarker(exception)) {
                throw exception;
            }

            throw new IllegalStateException(
                    "Market observer could not restore filtered catalog URL "
                            + catalogUrl
                            + " after reading listing publication times.",
                    exception
            );
        }
    }

    private void waitForSessionRefreshResolution(
            Page page,
            String requestedUrl
    ) {
        if (!MarketplaceUrls.isSessionRefreshUrl(page.url())) {
            return;
        }

        long deadline =
                System.currentTimeMillis()
                        + (long) SESSION_REFRESH_TIMEOUT_MS;

        while (System.currentTimeMillis() < deadline) {
            if (page.isClosed()) {
                throw new IllegalStateException(
                        "Vinted page closed while waiting for session-refresh during market publication lookup."
                );
            }

            if (!MarketplaceUrls.isSessionRefreshUrl(page.url())) {
                return;
            }

            page.waitForTimeout(SESSION_REFRESH_POLL_MS);
        }

        throw new IllegalStateException(
                "Vinted session-refresh remained stuck while market observer was navigating to "
                        + requestedUrl
                        + ". Current URL: "
                        + page.url()
        );
    }

    private void throwIfRateLimitedResponse(
            Response response,
            String operation,
            String listingId
    ) {
        if (response == null) {
            return;
        }

        int status = response.status();

        if (status == 403 || status == 429) {
            throw trafficBackoff(
                    "Vinted returned HTTP "
                            + status
                            + " during "
                            + operation
                            + (listingId == null
                            ? ""
                            : " for listing " + listingId)
            );
        }
    }

    private void throwIfBlockedPage(
            Page page,
            String listingId
    ) {
        try {
            Object rawBlocked = page.evaluate(MarketPublicationDomScripts.BLOCK_PAGE_SCRIPT);

            if (Boolean.TRUE.equals(rawBlocked)) {
                throw trafficBackoff(
                        "Vinted rendered a session/traffic block page"
                                + (listingId == null
                                ? ""
                                : " for listing " + listingId)
                );
            }
        } catch (RuntimeException exception) {
            if (containsTrafficBackoffMarker(exception)) {
                throw exception;
            }

            log.debug(
                    "[MARKET STATS] Block-page probe was inconclusive for listing {}. reason={}",
                    listingId,
                    safeMessage(exception)
            );
        }
    }

    private IllegalStateException trafficBackoff(String reason) {
        return new IllegalStateException(
                TRAFFIC_BACKOFF_MARKER + ": " + reason
        );
    }

    private String resolveTrustedListingUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "Market listing URL cannot be blank"
            );
        }

        URI resolved = URI.create(MarketplaceUrls.HOME)
                .resolve(rawUrl.trim());

        String trustedUrl = resolved.toString();

        if (!MarketplaceUrls.isVintedUrl(trustedUrl)) {
            throw new IllegalArgumentException(
                    "Refusing non-Vinted market listing URL: " + rawUrl
            );
        }

        return trustedUrl;
    }

    private boolean isExpectedListingUrl(
            String rawUrl,
            String listingId
    ) {
        if (!MarketplaceUrls.isVintedUrl(rawUrl)
                || listingId == null
                || listingId.isBlank()) {
            return false;
        }

        try {
            String path = URI.create(rawUrl).getPath();
            if (path == null) {
                return false;
            }

            String prefix = "/items/" + listingId.trim();
            return path.equals(prefix)
                    || path.startsWith(prefix + "-")
                    || path.startsWith(prefix + "/");
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static String extractPublishedAtScript() {
        return MarketPublicationDomScripts.EXTRACT_PUBLISHED_AT_SCRIPT;
    }

    private boolean containsTrafficBackoffMarker(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            String message = current.getMessage();

            if (message != null
                    && message.contains(TRAFFIC_BACKOFF_MARKER)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private boolean isAnonymousMarketObserver() {
        return context != null
                && context.getBot() != null
                && ANONYMOUS_MARKET_OBSERVER_NAME.equals(
                        context.getBot().getName()
                );
    }

    private String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }

        String message = throwable.getMessage();

        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        return message.lines()
                .findFirst()
                .orElse(message)
                .trim();
    }
}

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

    private static final String EXTRACT_PUBLISHED_AT_SCRIPT = """
            (listingId) => {
                const normalize = (value) =>
                    String(value ?? "")
                        .replace(/\\u00a0/g, " ")
                        .replace(/\\s+/g, " ")
                        .trim();

                const fromExactUploadDateField = () => {
                    const field = document.querySelector(
                        '[data-testid="item-attributes-upload_date"]'
                    );
                    if (!field) {
                        return null;
                    }

                    const value = field.querySelector(
                        '[itemprop="upload_date"]'
                    );
                    if (!value) {
                        return null;
                    }

                    const datetimeElement = value.matches('[datetime]')
                        ? value
                        : value.querySelector('[datetime]');
                    if (datetimeElement) {
                        const datetime = normalize(
                            datetimeElement.getAttribute('datetime')
                        );
                        if (datetime) {
                            return `ISO|${datetime}`;
                        }
                    }

                    const text = normalize(value.textContent);
                    return text ? `REL|${text}` : null;
                };

                const looksRelative = (value) => {
                    const lower = normalize(value).toLowerCase();
                    if (!lower) {
                        return false;
                    }

                    if (lower === "teraz"
                            || lower === "wczoraj"
                            || lower === "przedwczoraj"
                            || lower === "dzisiaj"
                            || lower.startsWith("przed chwil")) {
                        return true;
                    }

                    return /^\\d/.test(lower)
                        && ["sek", "min", "godz", " h", "dzie", "dni", " d", "tyg", "mies", "rok", "lat"]
                            .some(unit => lower.includes(unit));
                };

                const fromAddedLabel = () => {
                    if (!document.body) {
                        return null;
                    }

                    const walker = document.createTreeWalker(
                        document.body,
                        NodeFilter.SHOW_TEXT
                    );

                    const textNodes = [];
                    let node;

                    while ((node = walker.nextNode())) {
                        const text = normalize(node.nodeValue);
                        if (text) {
                            textNodes.push({ node, text });
                        }
                    }

                    for (let index = 0; index < textNodes.length; index++) {
                        const item = textNodes[index];
                        if (item.text.toLowerCase() !== "dodane") {
                            continue;
                        }

                        let container = item.node.parentElement;

                        for (let depth = 0; container && depth < 6; depth++) {
                            const time = container.querySelector("time[datetime]");
                            if (time) {
                                const datetime = normalize(
                                    time.getAttribute("datetime")
                                );
                                if (datetime) {
                                    return `ISO|${datetime}`;
                                }
                            }

                            const datetimeElement =
                                container.querySelector("[datetime]");
                            if (datetimeElement) {
                                const datetime = normalize(
                                    datetimeElement.getAttribute("datetime")
                                );
                                if (datetime) {
                                    return `ISO|${datetime}`;
                                }
                            }

                            const containerText = normalize(
                                container.textContent
                            );
                            if (containerText.toLowerCase().startsWith("dodane ")) {
                                const candidate = normalize(
                                    containerText.substring("dodane".length)
                                );
                                if (looksRelative(candidate)) {
                                    return `REL|${candidate}`;
                                }
                            }

                            container = container.parentElement;
                        }

                        for (
                            let nextIndex = index + 1;
                            nextIndex < Math.min(textNodes.length, index + 12);
                            nextIndex++
                        ) {
                            const candidate = textNodes[nextIndex].text;
                            if (looksRelative(candidate)) {
                                return `REL|${candidate}`;
                            }
                        }
                    }

                    return null;
                };

                const jsonLdDate = () => {
                    const dateKeys = [
                        "datePublished",
                        "uploadDate",
                        "dateCreated"
                    ];

                    const visit = (value) => {
                        if (Array.isArray(value)) {
                            for (const child of value) {
                                const found = visit(child);
                                if (found) {
                                    return found;
                                }
                            }
                            return null;
                        }

                        if (!value || typeof value !== "object") {
                            return null;
                        }

                        const rawType = value["@type"];
                        const types = Array.isArray(rawType)
                            ? rawType
                            : [rawType];

                        const isItem = types.some(type => {
                            const normalizedType =
                                normalize(type).toLowerCase();
                            return normalizedType === "product"
                                || normalizedType === "offer";
                        });

                        if (isItem) {
                            for (const key of dateKeys) {
                                const candidate = value[key];
                                if (typeof candidate === "string"
                                        && normalize(candidate)) {
                                    return normalize(candidate);
                                }
                            }
                        }

                        for (const child of Object.values(value)) {
                            const found = visit(child);
                            if (found) {
                                return found;
                            }
                        }

                        return null;
                    };

                    for (const script of document.querySelectorAll(
                        'script[type="application/ld+json"]'
                    )) {
                        try {
                            const found = visit(
                                JSON.parse(script.textContent || "null")
                            );
                            if (found) {
                                return found;
                            }
                        } catch (_) {
                            // Ignore malformed third-party JSON-LD.
                        }
                    }

                    return null;
                };

                const hydratedCreatedAt = () => {
                    const id = normalize(listingId);
                    if (!id || !document.documentElement) {
                        return null;
                    }

                    const slash = String.fromCharCode(92);
                    const raw = document.documentElement.outerHTML || "";
                    const decoded = raw
                        .split(`${slash}u0022`).join('"')
                        .split(`${slash}\"`).join('"');
                    const variants = decoded === raw
                        ? [raw]
                        : [raw, decoded];

                    const idNeedles = [
                        `"id":${id}`,
                        `"id":"${id}"`,
                        `"item_id":${id}`,
                        `"item_id":"${id}"`
                    ];
                    const timestampKey = '"created_at_ts"';

                    const readValue = (source, keyIndex) => {
                        const colon = source.indexOf(
                            ":",
                            keyIndex + timestampKey.length
                        );
                        if (colon < 0) {
                            return null;
                        }

                        let cursor = colon + 1;
                        while (cursor < source.length
                                && source.charCodeAt(cursor) <= 32) {
                            cursor++;
                        }

                        if (cursor >= source.length) {
                            return null;
                        }

                        const quote = source[cursor];
                        if (quote === '"' || quote === "'") {
                            cursor++;
                            let end = cursor;

                            while (end < source.length) {
                                if (source[end] === quote
                                        && source[end - 1] !== slash) {
                                    break;
                                }
                                end++;
                            }

                            if (end >= source.length) {
                                return null;
                            }

                            return normalize(source.slice(cursor, end));
                        }

                        let end = cursor;
                        while (end < source.length) {
                            const character = source[end];
                            if (character === ","
                                    || character === "}"
                                    || character === "]"
                                    || source.charCodeAt(end) <= 32) {
                                break;
                            }
                            end++;
                        }

                        return normalize(source.slice(cursor, end));
                    };

                    for (const source of variants) {
                        for (const needle of idNeedles) {
                            let cursor = 0;

                            while (cursor < source.length) {
                                const idIndex =
                                    source.indexOf(needle, cursor);
                                if (idIndex < 0) {
                                    break;
                                }

                                const from = Math.max(0, idIndex - 20000);
                                const to = Math.min(
                                    source.length,
                                    idIndex + 20000
                                );
                                const fragment = source.slice(from, to);
                                const localIdIndex = idIndex - from;

                                let timestampIndex =
                                    fragment.indexOf(timestampKey);
                                let bestValue = null;
                                let bestDistance =
                                    Number.POSITIVE_INFINITY;

                                while (timestampIndex >= 0) {
                                    const value = readValue(
                                        fragment,
                                        timestampIndex
                                    );

                                    if (value) {
                                        const distance = Math.abs(
                                            timestampIndex - localIdIndex
                                        );

                                        if (distance < bestDistance) {
                                            bestDistance = distance;
                                            bestValue = value;
                                        }
                                    }

                                    timestampIndex = fragment.indexOf(
                                        timestampKey,
                                        timestampIndex
                                            + timestampKey.length
                                    );
                                }

                                if (bestValue !== null) {
                                    return bestValue;
                                }

                                cursor = idIndex + needle.length;
                            }
                        }
                    }

                    return null;
                };

                const exactUploadDate = fromExactUploadDateField();
                if (exactUploadDate) {
                    return exactUploadDate;
                }

                const visibleAdded = fromAddedLabel();
                if (visibleAdded) {
                    return visibleAdded;
                }

                const absolute = jsonLdDate();
                if (absolute) {
                    return `ISO|${absolute}`;
                }

                const hydrated = hydratedCreatedAt();
                return hydrated
                    ? `ISO|${hydrated}`
                    : null;
            }
            """;

    private static final String BLOCK_PAGE_SCRIPT = """
            () => {
                const text = String(
                    document.body?.innerText
                        || document.documentElement?.innerText
                        || ""
                ).toLowerCase();

                return text.includes(
                    "access to this site is blocked for this computer"
                ) || text.includes(
                    "twoja sesja została zablokowana"
                );
            }
            """;

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
                    EXTRACT_PUBLISHED_AT_SCRIPT,
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
            Object rawBlocked = page.evaluate(BLOCK_PAGE_SCRIPT);

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
        return EXTRACT_PUBLISHED_AT_SCRIPT;
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

package pl.flipbot.playwright.marketstats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.scanner.model.Listing;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class MarketListingPublishedAtResolver {

    static final String TRAFFIC_BACKOFF_MARKER =
            "MARKET_STATS_TRAFFIC_BACKOFF";

    private static final String ANONYMOUS_MARKET_OBSERVER_NAME =
            "Anonymous Market Observer";
    private static final ZoneId MARKET_ZONE = ZoneId.of("Europe/Warsaw");
    private static final int MAX_DETAIL_FALLBACK_PER_BATCH = 3;

    private static final String EXTRACT_PUBLISHED_AT_SCRIPT = """
            async (entries) => {
                const normalize = (value) =>
                    String(value ?? "")
                        .replace(/\\u00a0/g, " ")
                        .replace(/\\s+/g, " ")
                        .trim();

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

                const fromAddedLabel = (document) => {
                    if (!document || !document.body) {
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

                        for (let depth = 0; container && depth < 5; depth++) {
                            const time = container.querySelector('time[datetime]');
                            if (time) {
                                const datetime = normalize(time.getAttribute("datetime"));
                                if (datetime) {
                                    return `ISO|${datetime}`;
                                }
                            }

                            const datetimeElement = container.querySelector('[datetime]');
                            if (datetimeElement) {
                                const datetime = normalize(
                                    datetimeElement.getAttribute("datetime")
                                );
                                if (datetime) {
                                    return `ISO|${datetime}`;
                                }
                            }

                            const containerText = normalize(container.textContent);
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
                            nextIndex < Math.min(textNodes.length, index + 10);
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

                const hydratedCreatedAt = (html, listingId) => {
                    const id = String(listingId ?? "").trim();
                    if (!id) {
                        return null;
                    }

                    const slash = String.fromCharCode(92);
                    const raw = String(html ?? "");
                    const decoded = raw
                        .split(`${slash}u0022`).join('"')
                        .split(`${slash}"`).join('"');
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
                                const idIndex = source.indexOf(needle, cursor);
                                if (idIndex < 0) {
                                    break;
                                }

                                const from = Math.max(0, idIndex - 20000);
                                const to = Math.min(source.length, idIndex + 20000);
                                const fragment = source.slice(from, to);
                                const localIdIndex = idIndex - from;

                                let timestampIndex = fragment.indexOf(timestampKey);
                                let bestValue = null;
                                let bestDistance = Number.POSITIVE_INFINITY;

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
                                        timestampIndex + timestampKey.length
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

                const jsonLdDate = (document) => {
                    const dateKeys = ["datePublished", "uploadDate", "dateCreated"];

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
                        const types = Array.isArray(rawType) ? rawType : [rawType];
                        const isItem = types.some(type => {
                            const normalizedType = normalize(type).toLowerCase();
                            return normalizedType === "product"
                                || normalizedType === "offer";
                        });

                        if (isItem) {
                            for (const key of dateKeys) {
                                const candidate = value[key];
                                if (typeof candidate === "string" && normalize(candidate)) {
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

                    for (const script of document.querySelectorAll('script[type="application/ld+json"]')) {
                        try {
                            const found = visit(JSON.parse(script.textContent || "null"));
                            if (found) {
                                return found;
                            }
                        } catch (_) {
                            // Ignore malformed third-party JSON-LD.
                        }
                    }

                    return null;
                };

                const result = {};
                let trafficBackoff = null;
                let lastRequestStartedAt = 0;
                const requestSpacingMs = 2000;

                const paceRequest = async () => {
                    const now = Date.now();
                    const waitMs = Math.max(
                        0,
                        requestSpacingMs - (now - lastRequestStartedAt)
                    );
                    if (waitMs > 0) {
                        await new Promise(resolve => setTimeout(resolve, waitMs));
                    }
                    lastRequestStartedAt = Date.now();
                };

                const fetchOne = async (entry) => {
                    if (trafficBackoff) {
                        return null;
                    }

                    await paceRequest();

                    const controller = new AbortController();
                    const timeout = setTimeout(() => controller.abort(), 7000);

                    try {
                        const url = new URL(entry.url, window.location.href).toString();
                        const response = await fetch(url, {
                            credentials: "include",
                            redirect: "follow",
                            signal: controller.signal,
                            headers: {
                                "Accept": "text/html,application/xhtml+xml"
                            }
                        });

                        if (response.status === 429 || response.status === 403) {
                            trafficBackoff = `HTTP_${response.status}`;
                            return null;
                        }

                        if (!response.ok) {
                            return null;
                        }

                        const html = await response.text();
                        const lowerHtml = html.toLowerCase();

                        if (lowerHtml.includes("access to this site is blocked for this computer")
                                || lowerHtml.includes("twoja sesja została zablokowana")) {
                            trafficBackoff = "BLOCK_PAGE";
                            return null;
                        }

                        const hydrated = hydratedCreatedAt(html, entry.id);
                        if (hydrated) {
                            return [entry.id, `ISO|${hydrated}`];
                        }

                        const document = new DOMParser().parseFromString(
                            html,
                            "text/html"
                        );

                        const visibleAdded = fromAddedLabel(document);
                        if (visibleAdded) {
                            return [entry.id, visibleAdded];
                        }

                        const absolute = jsonLdDate(document);
                        return absolute
                            ? [entry.id, `ISO|${absolute}`]
                            : null;
                    } catch (_) {
                        return null;
                    } finally {
                        clearTimeout(timeout);
                    }
                };

                for (const entry of entries) {
                    if (trafficBackoff) {
                        break;
                    }

                    const resolved = await fetchOne(entry);
                    if (resolved) {
                        result[resolved[0]] = resolved[1];
                    }
                }

                if (trafficBackoff) {
                    result["__FLIPBOT_TRAFFIC_BACKOFF__"] = trafficBackoff;
                }

                return result;
            }
            """;

    private final BotContext context;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MarketListingPublishedAtResolver(BotContext context) {
        this.context = context;
    }

    public void captureIfNeeded(List<Listing> listings) {
        if (!isAnonymousMarketObserver()
                || listings == null
                || listings.isEmpty()) {
            return;
        }

        /*
         * Prefer timestamps already hydrated into the filtered catalog page.
         * This adds zero Vinted requests and, when available, resolves the whole
         * visible batch before the detail-page fallback is considered.
         */
        new MarketCatalogPublishedAtResolver(context)
                .captureIfAvailable(listings);

        List<Map<String, String>> entries = new ArrayList<>();

        for (Listing listing : listings) {
            if (listing == null
                    || listing.getId() == null
                    || listing.getId().isBlank()
                    || listing.getUrl() == null
                    || listing.getUrl().isBlank()) {
                continue;
            }

            MarketStatsObservationContext.recordObservedListingId(
                    listing.getId()
            );

            if (entries.size() >= MAX_DETAIL_FALLBACK_PER_BATCH) {
                continue;
            }

            if (!MarketStatsObservationContext.claimPublicationResolution(
                    listing.getId()
            )) {
                continue;
            }

            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("id", listing.getId().trim());
            entry.put("url", listing.getUrl().trim());
            entries.add(entry);
        }

        if (entries.isEmpty()) {
            return;
        }

        try {
            Object rawResult = context.getPage().evaluate(
                    EXTRACT_PUBLISHED_AT_SCRIPT,
                    entries
            );

            Map<String, String> payloads = objectMapper.convertValue(
                    rawResult,
                    new TypeReference<Map<String, String>>() {
                    }
            );

            String trafficBackoff = payloads.remove(
                    "__FLIPBOT_TRAFFIC_BACKOFF__"
            );

            LocalDateTime observedAt = LocalDateTime.now(MARKET_ZONE);
            Set<String> resolvedIds = new HashSet<>();

            for (Map.Entry<String, String> entry : payloads.entrySet()) {
                VintedPublishedAtParser.parse(
                                entry.getValue(),
                                observedAt
                        )
                        .ifPresent(publishedAt -> {
                            MarketStatsObservationContext.recordPublishedAt(
                                    entry.getKey(),
                                    publishedAt
                            );
                            resolvedIds.add(entry.getKey());

                            log.debug(
                                    "[MARKET STATS] Vinted publication time resolved. listingId={}, publishedAt={}, source='{}'.",
                                    entry.getKey(),
                                    publishedAt,
                                    entry.getValue()
                            );
                        });
            }

            List<String> unresolvedIds = entries.stream()
                    .map(entry -> entry.get("id"))
                    .filter(id -> id != null && !resolvedIds.contains(id))
                    .toList();

            log.info(
                    "[MARKET STATS] Publication detail fallback resolved {}/{} requested listing timestamps with paced sequential requests.",
                    resolvedIds.size(),
                    entries.size()
            );

            if (!unresolvedIds.isEmpty() && (trafficBackoff == null || trafficBackoff.isBlank())) {
                log.warn(
                        "[MARKET STATS] Publication timestamp still unresolved for {}/{} detail fallbacks. sampleIds={}",
                        unresolvedIds.size(),
                        entries.size(),
                        unresolvedIds.stream().limit(8).toList()
                );
            }

            /*
             * Preserve any timestamps that were resolved before a 403/429. The
             * caller's interrupted-context cleanup will flush them to backend,
             * then the normal traffic-backoff policy still stops this pass.
             */
            if (trafficBackoff != null && !trafficBackoff.isBlank()) {
                throw new IllegalStateException(
                        TRAFFIC_BACKOFF_MARKER
                                + ": Vinted asked the market observer to back off while reading publication details. signal="
                                + trafficBackoff
                );
            }
        } catch (RuntimeException exception) {
            if (containsTrafficBackoffMarker(exception)) {
                throw exception;
            }

            log.warn(
                    "[MARKET STATS] Could not inspect {} listing detail pages for Vinted publication time.",
                    entries.size(),
                    exception
            );
        }
    }

    static String extractPublishedAtScript() {
        return EXTRACT_PUBLISHED_AT_SCRIPT;
    }

    private boolean containsTrafficBackoffMarker(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(TRAFFIC_BACKOFF_MARKER)) {
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
}

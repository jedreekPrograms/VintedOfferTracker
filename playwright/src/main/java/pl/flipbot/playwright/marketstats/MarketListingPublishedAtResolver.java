package pl.flipbot.playwright.marketstats;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import pl.flipbot.playwright.context.BotContext;
import pl.flipbot.playwright.scanner.model.Listing;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class MarketListingPublishedAtResolver {

    private static final String ANONYMOUS_MARKET_OBSERVER_NAME =
            "Anonymous Market Observer";
    private static final ZoneId MARKET_ZONE = ZoneId.of("Europe/Warsaw");

    private static final String EXTRACT_PUBLISHED_AT_SCRIPT = """
            async (entries) => {
                const normalize = (value) =>
                    String(value ?? "")
                        .replace(/\u00a0/g, " ")
                        .replace(/\s+/g, " ")
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
                        && ["sek", "min", "godz", " h", "dzie", "dni", " d", "tyg", "mies"]
                            .some(unit => lower.includes(unit));
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

                const fromAddedLabel = (document) => {
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

                const fetchOne = async (entry) => {
                    const controller = new AbortController();
                    const timeout = setTimeout(() => controller.abort(), 7000);

                    try {
                        const response = await fetch(entry.url, {
                            credentials: "include",
                            redirect: "follow",
                            signal: controller.signal,
                            headers: {
                                "Accept": "text/html,application/xhtml+xml"
                            }
                        });

                        if (!response.ok) {
                            return null;
                        }

                        const html = await response.text();
                        const document = new DOMParser().parseFromString(
                            html,
                            "text/html"
                        );

                        const absolute = jsonLdDate(document);
                        if (absolute) {
                            return [entry.id, `ISO|${absolute}`];
                        }

                        const relative = fromAddedLabel(document);
                        return relative ? [entry.id, relative] : null;
                    } catch (_) {
                        return null;
                    } finally {
                        clearTimeout(timeout);
                    }
                };

                const result = {};
                let cursor = 0;
                const workerCount = Math.min(3, entries.length);

                const worker = async () => {
                    while (cursor < entries.length) {
                        const index = cursor++;
                        const resolved = await fetchOne(entries[index]);
                        if (resolved) {
                            result[resolved[0]] = resolved[1];
                        }
                    }
                };

                await Promise.all(
                    Array.from({ length: workerCount }, () => worker())
                );

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

        List<Map<String, String>> entries = new ArrayList<>();

        for (Listing listing : listings) {
            if (listing == null
                    || listing.getId() == null
                    || listing.getId().isBlank()
                    || listing.getUrl() == null
                    || listing.getUrl().isBlank()) {
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

            LocalDateTime observedAt = LocalDateTime.now(MARKET_ZONE);

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

                            log.debug(
                                    "[MARKET STATS] Vinted publication time resolved. listingId={}, publishedAt={}, source='{}'.",
                                    entry.getKey(),
                                    publishedAt,
                                    entry.getValue()
                            );
                        });
            }
        } catch (RuntimeException exception) {
            log.warn(
                    "[MARKET STATS] Could not enrich {} new listings with Vinted publication time. "
                            + "Statistics will safely fall back to observer first-seen time for unresolved listings.",
                    entries.size(),
                    exception
            );
        }
    }

    private boolean isAnonymousMarketObserver() {
        return context != null
                && context.getBot() != null
                && ANONYMOUS_MARKET_OBSERVER_NAME.equals(
                        context.getBot().getName()
                );
    }
}
